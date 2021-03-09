package com.example.campus_activity.ui.chat

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.campus_activity.R
import com.example.campus_activity.data.model.ChatModel
import com.example.campus_activity.ui.adapter.ChatAdapter
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.AndroidEntryPoint
import java.time.Instant
import java.util.*
import javax.inject.Inject
import kotlin.collections.ArrayList
import kotlin.time.ExperimentalTime

@RequiresApi(Build.VERSION_CODES.O)
@AndroidEntryPoint
class ChatActivity : AppCompatActivity() {

    //  Test realtime database
    private val fireDatabase = FirebaseDatabase.getInstance()
    private var chatsCount : Long = 0
    private val chatsReference = fireDatabase.getReference("chats")
    private var testEndPoint = chatsReference.child("test")
    private var roomName = "test01"
    private var isAdmin = false

    //  Hilt variables
    @Inject
    lateinit var recyclerViewAdapter: ChatAdapter
    @Inject
    lateinit var firebaseAuth: FirebaseAuth
    @Inject
    lateinit var firebaseFirestore: FirebaseFirestore

    //  Variable declaration
    private lateinit var toolbar:Toolbar
    private lateinit var progressBar:ProgressBar
    private lateinit var messageEditText: TextInputEditText
    private lateinit var sendButton:FloatingActionButton
    private lateinit var dateMaterialCard:MaterialCardView
    private lateinit var dateTextView:TextView
    private lateinit var recyclerView:RecyclerView
    private lateinit var fabScrollToBottom:FloatingActionButton
    private var chats:ArrayList<ChatModel> = ArrayList()

    @ExperimentalTime
    @SuppressLint("UseCompatLoadingForDrawables")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        //  Add endpoint
        try {
            roomName = intent.getStringExtra("roomName")!!
            isAdmin = intent.getBooleanExtra("isClubAdmin", false)
            testEndPoint = chatsReference.child(intent.getStringExtra("roomName")!!)
        }catch (e:Exception){
            Toast.makeText(this, "Unidentified room!", Toast.LENGTH_SHORT).show()
        }

        //  Variable assignment
        toolbar = findViewById(R.id.chat_toolbar)
        progressBar = findViewById(R.id.chat_progress_bar)
        messageEditText = findViewById(R.id.message_edit_text)
        sendButton = findViewById(R.id.send_message_button)
        dateMaterialCard = findViewById(R.id.chat_date_mat_card)
        dateTextView = findViewById(R.id.chat_date_text_view)
        recyclerView = findViewById(R.id.chat_recycler_view)
        fabScrollToBottom = findViewById(R.id.foa_scroll_to_bottom)

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = recyclerViewAdapter
        fabScrollToBottom.hide()

        //  Chat realtime listener
        val chatListener = object : ValueEventListener{
            override fun onDataChange(snapshot: DataSnapshot) {
                if(chats.size == 0){
                    loadAllChats(snapshot)
                }
                else if(snapshot.childrenCount.toInt() > chatsCount){
                    addNewChatsOnChange(snapshot)
                }
                chatsCount = snapshot.childrenCount
            }

            override fun onCancelled(error: DatabaseError) {
            }
        }

        //  Initialize action bar
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = roomName
        dateMaterialCard.visibility = View.INVISIBLE

        //  Set the background of the chat
        window.setBackgroundDrawable(resources.getDrawable(R.drawable.background))

        //  Scroll to bottom on keyboard pop
        recyclerView.addOnLayoutChangeListener { _, _, _, _, bottom, _, _, _, oldBottom ->
            if(bottom < oldBottom){
                recyclerView.smoothScrollToPosition(bottom)
            }
        }

        setUpScrollToBottom()

        //  Send message
        sendButton.setOnClickListener {
            if(messageEditText.text.toString() != ""){
                insertChatOnClick(messageEditText.text.toString())
                messageEditText.setText("")
            }
        }

        //  Add chat listener
        testEndPoint.addValueEventListener(chatListener)
    }

    //  Scroll to bottom
    @ExperimentalTime
    private fun setUpScrollToBottom(){
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener(){
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val currentBottomPosition = (recyclerView.layoutManager as LinearLayoutManager).findLastVisibleItemPosition()
                val currentCompleteTopPosition = (recyclerView.layoutManager as LinearLayoutManager).findFirstCompletelyVisibleItemPosition()
                val currentTopPosition = (recyclerView.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()

                //  Handle fab icon
                if(currentBottomPosition == chats.size - 1){
                    fabScrollToBottom.hide()
                }
                else{
                    fabScrollToBottom.show()
                }

                //  Handle date text
                if (currentCompleteTopPosition == 0){
                    dateMaterialCard.animate().translationY(-100F).alpha(0F)
                }
                else{
                    dateMaterialCard.animate().translationY(0F).alpha(1F)
                }

                val day = recyclerViewAdapter.getDay(chats[currentTopPosition].timestamp)
                dateTextView.text = day

                super.onScrolled(recyclerView, dx, dy)
            }
        })
        fabScrollToBottom.setOnClickListener {
            recyclerView.smoothScrollToPosition(recyclerView.bottom)
        }
    }

    //  Load chats on startup
    private fun loadAllChats(snapshot : DataSnapshot){
        try {
            val array = snapshot.value as ArrayList<*>
            for(i in array){
                val newChat = convertToChat(i as HashMap<*, *>)
                insertChat(newChat)
            }
            recyclerViewAdapter.setChats(chats)
            recyclerView.scrollToPosition(chats.size - 1)
        }catch (e:Exception){
            e.printStackTrace()
        }
        progressBar.visibility = View.INVISIBLE
        dateMaterialCard.visibility = View.VISIBLE
    }

    //  Add chats on change
    private fun addNewChatsOnChange(snapshot: DataSnapshot){
        try {
            val newChatHash = (snapshot.value as ArrayList<*>)[snapshot.childrenCount.toInt() - 1] as HashMap<*,*>
            val newChat = convertToChat(newChatHash)
            insertChat(newChat)
            recyclerViewAdapter.addChat()
            recyclerView.scrollToPosition(chats.size - 1)
        }catch (e:Exception){
            e.printStackTrace()
        }
    }

    //  Convert map to chat model
    private fun convertToChat(hashMap: HashMap<*, *>) : ChatModel{
        return ChatModel(
                hashMap["id"] as Long,
                hashMap["sender"] as String,
                hashMap["senderMail"] as String,
                hashMap["message"]as String,
                convertToTimestamp(hashMap["timestamp"] as HashMap<*, *>)
        )
    }

    //  Convert map to timestamp
    private fun convertToTimestamp(hashMap: HashMap<*, *>) : Timestamp{
        return Timestamp(hashMap["seconds"] as Long, (hashMap["nanoseconds"] as Long).toInt())
    }

    //  Insert chat by model
    private fun insertChat(chat:ChatModel): ChatModel {
        chats.add(chat)
        recyclerView.scrollToPosition(chats.size - 1)
        return chat
    }

    //  Insert message by "You"
    private fun insertChatOnClick(message:String){
        val time = Timestamp(Date.from(Instant.now()))
        val user = firebaseAuth.currentUser
        val newChat = ChatModel(chatsCount, user?.displayName!!, user.email!!, message, time)

        //  Test database
        testEndPoint.child("$chatsCount").setValue(newChat)
    }

    //  Options create
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_chat_activity, menu)
        menu?.findItem(R.id.add_member)?.isVisible = isAdmin
        return super.onCreateOptionsMenu(menu)
    }

    //  Option selected
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId){
            android.R.id.home -> {
                finish()
                true
            }
            R.id.add_member -> {
                true
            }
            else -> false
        }
    }

}