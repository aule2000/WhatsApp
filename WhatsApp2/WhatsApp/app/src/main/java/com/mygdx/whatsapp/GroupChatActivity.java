package com.mygdx.whatsapp;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.squareup.picasso.Picasso;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;

public class GroupChatActivity extends AppCompatActivity {

    private FirebaseAuth firebaseAuth;

    private String groupId, myGroupRole="";

    private Toolbar toolbar;

    private ImageView groupIconIv;
    private TextView groupTitleTv;
    private ImageButton attachBtn, sendBtn, sendcdBtn;
    private EditText messageEt;
    private RecyclerView chatRv;

    private ArrayList<ModelGroupChat> groupChatList;
    private AdapterGroupChat adapterGroupChat;

    private static final int CAMERA_REQUEST_CODE = 200;
    private static final int STORAGE_REQUEST_CODE = 400;

    private static final int IMAGE_PICK_GALLERY_CODE = 1000;
    private static final int IMAGE_PICK_CAMERA_CODE = 2000;

    private String[] cameraPermission;
    private String[] storagePermission;

    String message = "";
    String messagecode = "";
    String parentStringValue1 = "";

    private static Socket s;
    private static PrintWriter printWriter;

    private Uri image_uri = null;

    private static String ip = "192.168.8.100";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_chat);

        toolbar = (Toolbar) findViewById(R.id.toolbar);
        groupIconIv = findViewById(R.id.groupIconIv);
        groupTitleTv = findViewById(R.id.groupTitleTv);
        attachBtn = findViewById(R.id.attachBtn);
        messageEt = findViewById(R.id.messageEt);
        sendBtn = findViewById(R.id.sendBtn);
        chatRv = findViewById(R.id.chatRv);
        sendcdBtn = findViewById(R.id.sendcdBtn);

        setSupportActionBar(toolbar);

        Intent intent = getIntent();
        groupId = intent.getStringExtra("groupId");

        cameraPermission = new String[]{
                Manifest.permission.CAMERA,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        };
        storagePermission = new String[]{
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        };

        firebaseAuth = FirebaseAuth.getInstance();
        loadGroupInfo();
        loadGroupMessages();
        loadMyGroupRole();


        sendBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                message = messageEt.getText().toString().trim();
                if(message.startsWith("run")){
                    parentStringValue1 = message.substring(message.indexOf(" ")+1);
                    myTask2 mt = new myTask2();
                    mt.execute();
                }

                if(TextUtils.isEmpty(message)){
                    Toast.makeText(GroupChatActivity.this, "Can't send empty message", Toast.LENGTH_SHORT).show();
                }
                else{
                    sendMessage(message);
                }
            }
        });
        sendcdBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                messagecode = messageEt.getText().toString().trim();
                if(TextUtils.isEmpty(messagecode)){
                    Toast.makeText(GroupChatActivity.this, "Can't send empty message", Toast.LENGTH_SHORT).show();
                }
                else{
                    sendcode(messagecode);
                }
            }
        });
        attachBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showImageImportDialog();
            }
        });

    }

    class myTask2 extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... voids) {


            try {
                s = new Socket(ip,5000);
                printWriter = new PrintWriter(s.getOutputStream());
                printWriter.write(parentStringValue1);
                printWriter.flush();
                printWriter.close();
                s.close();



            } catch (IOException e) {
                e.printStackTrace();
            }


            return null;
        }
    }

    private void showImageImportDialog() {
        String[] options = {"Camera", "Gallery"};

        AlertDialog.Builder builder= new AlertDialog.Builder(this);
        builder.setTitle("Pick Image")
                .setItems(options, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if(which == 0){
                            if(!checkCameraPermission()){
                                requestCameraPermission();
                            }
                            else{
                                pickCamera();
                            }
                        }
                        else{
                            if(!checkStoragePermission()){
                                requestStoragePermission();
                            }
                            else{
                                pickGallery();
                            }
                        }
                    }
                }).show();
    }

    private void pickGallery(){
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        startActivityForResult(intent,IMAGE_PICK_GALLERY_CODE);
    }

    private void pickCamera(){
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.Images.Media.TITLE, "GroupImageTitle");
        contentValues.put(MediaStore.Images.Media.DESCRIPTION, "GroupImageDescription");

        image_uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,contentValues);

        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT,image_uri);
        startActivityForResult(intent,IMAGE_PICK_CAMERA_CODE);
    }

    private void requestStoragePermission(){
        ActivityCompat.requestPermissions(this, storagePermission,STORAGE_REQUEST_CODE);
    }

    private boolean checkStoragePermission(){
        boolean result = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) == (PackageManager.PERMISSION_GRANTED);
        return result;
    }

    private void requestCameraPermission(){
        ActivityCompat.requestPermissions(this, cameraPermission,CAMERA_REQUEST_CODE);
    }

    private boolean checkCameraPermission(){
        boolean result = ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA) == (PackageManager.PERMISSION_GRANTED);
        boolean result1 = ContextCompat.checkSelfPermission(this,Manifest.permission.WRITE_EXTERNAL_STORAGE) == (PackageManager.PERMISSION_GRANTED);

        return result && result1;
    }

    private void sendImageMessage() {
        final ProgressDialog pd = new ProgressDialog(this);
        pd.setTitle("Please wait");
        pd.setMessage("Sending Image...");
        pd.setCanceledOnTouchOutside(false);
        pd.show();

        String filenamePath = "ChatImages/" + ""+System.currentTimeMillis();

        StorageReference storageReference = FirebaseStorage.getInstance().getReference(filenamePath);
        storageReference.putFile(image_uri)
                .addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                    @Override
                    public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                        Task<Uri> p_uriTask = taskSnapshot.getStorage().getDownloadUrl();
                        while(!p_uriTask.isSuccessful());
                        Uri p_downloadUri = p_uriTask.getResult();

                        if(p_uriTask.isSuccessful()){
                            String timestamp = ""+System.currentTimeMillis();
                            HashMap<String, Object>hashMap = new HashMap<>();
                            hashMap.put("sender",""+firebaseAuth.getUid());
                            hashMap.put("message",""+p_downloadUri);
                            hashMap.put("timestamp",""+timestamp);
                            hashMap.put("type",""+"image");

                            DatabaseReference ref = FirebaseDatabase.getInstance().getReference("Groups");
                            ref.child(groupId).child("Messages").child(timestamp).setValue(hashMap).addOnSuccessListener(new OnSuccessListener<Void>() {
                                @Override
                                public void onSuccess(Void aVoid) {
                                    messageEt.setText("");
                                    pd.dismiss();

                                }
                            }).addOnFailureListener(new OnFailureListener() {
                                @Override
                                public void onFailure(@NonNull Exception e) {
                                    pd.dismiss();
                                    Toast.makeText(GroupChatActivity.this, ""+e.getMessage(), Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Toast.makeText(GroupChatActivity.this, ""+e.getMessage(), Toast.LENGTH_SHORT).show();
                        pd.dismiss();
                    }
                });
    }


    private void loadMyGroupRole() {
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("Groups");;
        ref.child(groupId).child("Participants")
                .orderByChild("uid").equalTo(firebaseAuth.getUid())
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        for(DataSnapshot ds: dataSnapshot.getChildren()){
                            myGroupRole = ""+ds.child("role").getValue();
                            invalidateOptionsMenu();
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {

                    }
                });
    }

    private void loadGroupMessages() {

        groupChatList = new ArrayList<>();

        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("Groups");
        ref.child(groupId).child("Messages").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                groupChatList.clear();
                for(DataSnapshot ds: dataSnapshot.getChildren()){
                    ModelGroupChat model = ds.getValue(ModelGroupChat.class);
                    groupChatList.add(model);
                }
                adapterGroupChat = new AdapterGroupChat(GroupChatActivity.this,groupChatList);
                chatRv.setAdapter(adapterGroupChat);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
    }

    private void sendMessage(final String message) {
        String timestamp = ""+System.currentTimeMillis();
        HashMap<String, Object>hashMap = new HashMap<>();
        hashMap.put("sender",""+firebaseAuth.getUid());
        hashMap.put("message",""+message);
        hashMap.put("timestamp",""+timestamp);
        hashMap.put("type",""+"text");

        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("Groups");
        ref.child(groupId).child("Messages").child(timestamp).setValue(hashMap).addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void aVoid) {
                if(message.equals("reset")){
                    myTask3 mt = new myTask3();
                    mt.execute();
                }
                messageEt.setText("");

            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Toast.makeText(GroupChatActivity.this, ""+e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

    }

    class myTask3 extends AsyncTask<Void, Void, Void>{

        @Override
        protected Void doInBackground(Void... voids) {
            try {
                s = new Socket(ip,5000);
                printWriter = new PrintWriter(s.getOutputStream());
                printWriter.write(message);
                printWriter.flush();
                printWriter.close();
                s.close();
            } catch (IOException e) {
                e.printStackTrace();
            }


            return null;
        }
    }

    private void sendcode(final String message) {

        myTask mt = new myTask();
        mt.execute();

        String timestamp = ""+System.currentTimeMillis();
        HashMap<String, Object>hashMap = new HashMap<>();
        hashMap.put("sender",""+firebaseAuth.getUid());
        hashMap.put("message",""+message);
        hashMap.put("timestamp",""+timestamp);
        hashMap.put("type",""+"text");

        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("Groups");
        ref.child(groupId).child("Messages").child(timestamp).setValue(hashMap).addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void aVoid) {

                messageEt.setText("");

            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Toast.makeText(GroupChatActivity.this, ""+e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

    }

    class myTask extends AsyncTask<Void, Void, Void>{

        @Override
        protected Void doInBackground(Void... voids) {
            try {
                s = new Socket(ip,5000);
                printWriter = new PrintWriter(s.getOutputStream());
                printWriter.write(messagecode);
                printWriter.flush();
                printWriter.close();
                s.close();
            } catch (IOException e) {
                e.printStackTrace();
            }


            return null;
        }
    }

    private void loadGroupInfo() {
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("Groups");
        ref.orderByChild("groupId").equalTo(groupId)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        for(DataSnapshot ds: dataSnapshot.getChildren()){
                            String groupTitle = ""+ds.child("groupTitle").getValue();
                            String groupDescription = ""+ds.child("groupDescription").getValue();
                            String groupIcon = ""+ds.child("groupIcon").getValue();
                            String timestamp = ""+ds.child("timestamp").getValue();
                            String createdBy = ""+ds.child("createdBy").getValue();

                            groupTitleTv.setText(groupTitle);
                            try{
                                Picasso.get().load(groupIcon).placeholder(R.drawable.ic_group_white_background).into(groupIconIv);
                            }
                            catch (Exception e){
                                groupIconIv.setImageResource(R.drawable.ic_group_white_background);                  //gali but blogai
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {

                    }
                });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.options_menu,menu);

        menu.findItem(R.id.main_settings_option).setVisible(false);
        menu.findItem(R.id.main_create_group_option).setVisible(false);
        menu.findItem(R.id.main_find_friends_option).setVisible(false);
        menu.findItem(R.id.main_logout_option).setVisible(false);


        if(myGroupRole.equals("creator") || myGroupRole.equals("admin")){
            menu.findItem(R.id.action_add_participant).setVisible(true);
        }
        else{
            menu.findItem(R.id.action_add_participant).setVisible(false);
        }

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if(id == R.id.action_add_participant){
            Intent intent = new Intent(GroupChatActivity.this, GroupParticipantAddActivity.class);
            intent.putExtra("groupId",groupId);
            startActivity(intent);
        }

        return super.onOptionsItemSelected(item);
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(resultCode == RESULT_OK){
            if(requestCode == IMAGE_PICK_GALLERY_CODE){
                image_uri = data.getData();
                sendImageMessage();
            }
            if(requestCode == IMAGE_PICK_CAMERA_CODE){
                sendImageMessage();
            }
        }
    }



    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode){
            case CAMERA_REQUEST_CODE:
                if(grantResults.length > 0){
                    boolean cameraAccepted = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                    boolean writeStorageAccepted = grantResults[1] == PackageManager.PERMISSION_GRANTED;

                    if(cameraAccepted && writeStorageAccepted){
                        pickCamera();
                    }
                    else{
                        Toast.makeText(this, "Camera and Storage permissions are required", Toast.LENGTH_SHORT).show();
                    }
                }
                break;
            case STORAGE_REQUEST_CODE:
                if(grantResults.length > 0){
                    boolean writeStorageAccepted = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                    if(writeStorageAccepted){
                        pickGallery();
                    }
                    else{
                        Toast.makeText(this, "Storage permission is required", Toast.LENGTH_SHORT).show();
                    }
                }
                break;

        }
    }
}
