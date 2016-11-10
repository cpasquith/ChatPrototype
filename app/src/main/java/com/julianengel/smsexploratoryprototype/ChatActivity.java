package com.julianengel.smsexploratoryprototype;

import android.content.Intent;
import android.os.Handler;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;
import android.support.design.widget.NavigationView;

import com.parse.FindCallback;
import com.parse.LogInCallback;
import com.parse.ParseAnonymousUtils;
import com.parse.ParseException;
import com.parse.ParseObject;
import com.parse.ParseQuery;
import com.parse.ParseUser;
import com.parse.SaveCallback;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static android.support.design.R.styleable.MenuItem;


public class ChatActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    private ListView chatList;

    /* I'd imagine that this would be changed to ArrayAdapter<some subclass of ParseObject>
    down the line...
     */
    private ArrayAdapter<String> chatListAdapter;

    /* Declare a constant for the name of this class; used when sending things
     * to LogCat
     */
    static final String TAG = ChatActivity.class.getSimpleName();

    /* These are keys for the backend DB, and are the same as the ones defined
     * in Messages.java
     */
    static final String USER_ID_KEY = "userId";
    static final String BODY_KEY = "body";
    static final int LOGIN_REQUEST_CODE = 1;
    /* These hold the send button and message GUI elements that are defined in
     * activity_chat.xml. etMessage is the text the user wants to send and
     * btSend is the send button.
     */
    EditText etMessage;
    Button btSend;

    /* Will point to the ListView inside of the activity_chat.xml file. this is
     * where the messages will appear when sent and receieved.
     */
    ListView lvChat;

    /* mMessages will contain all the messages when the app queries the server
     * for new messages.
     */
    static ArrayList<Message> mMessages;

    /* Create an instance of our own ChatListAdapter. This class sits between
     * the data we get from the Parse server - in this case an ArrayList of
     * Messages - and the ListView in our app's layout.
     */
    ChatListAdapter mAdapter;

	/* This value determines if we're loading the app for the first time.
	 * It will affect the messages that we see upon first loading the app.
	 */
    static boolean mFirstLoad;

	/* How many elements to fetch when querying the Parse database for messages.
	 */
    static final int MAX_MESSAGES_TO_SHOW = 50;

	/* How often to poll the DB for new messages. Note that this is a TERRIBLE
	 * way to handle getting new messages! We should implement something a bit
	 * more intelligent for this.
	 */
    static final int POLL_INTERVAL = 1000;

	/* These two objects allow us to multithread the process of fetching new
	 * messages.
	 * A Handler manages messages and runnable code, allowing them to be passed
	 * 		to a Looper that is constantly queueing and processing messages.
	 * A Runnable is an object that represents code that can be run inside of
	 * 		a thread.
	 */
    Handler mHandler = new Handler();
    Runnable mRefreshMessagesRunnable = new Runnable() {
		/* The run() method is a part of the Runnable interface and is the
		*  code that is to be executed in a thread.
		*/
        @Override
        public void run() {
            refreshMessages();

			/* postDelayed(Runnable, long) will cause the runnable provided to
			 * be queued to run after a set amount of time.
			 * So this part of the code implements the "pull new messages every
			 * second" aspect.
			 * Essentially this thread runs refreshMessages(), then reschedules
			 * itsef.
			 */
            mHandler.postDelayed(this, POLL_INTERVAL);

        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
		/* Code that executes whenever this Activity is created; runs before
		 * the Activity begins its execution.
		 */

        super.onCreate(savedInstanceState);

		/* Set the current View to the one we have specified for this app,
		 * in activity_chat.xml
		 */
        setContentView(R.layout.activity_chat);

        /* Nav drawer and settings menu code implemented by Julian 11/3/16 */
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);

        /* Reference to our ListView containing all the chats, inside the Nav Drawer
         */
        chatList = (ListView) findViewById(R.id.chatList);

        String[] testChats = {"Chat 1", "Chat 2", "Chat 3"};
        chatListAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, testChats);
        chatList.setAdapter(chatListAdapter);


        /* Define logic for populating the menu in the nav drawer
            Implemented by Julian 11/7/16
         */
        drawer.addDrawerListener(new DrawerLayout.DrawerListener() {
            @Override
            public void onDrawerSlide(View drawerView, float slideOffset) {

            }

            @Override
            public void onDrawerOpened(View drawerView) {

            }

            @Override
            public void onDrawerClosed(View drawerView) {

            }

            @Override
            public void onDrawerStateChanged(int newState) {

            }
        });

        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();



		/* The ParseUser class is a local representation of a user's data: name,
		 * email, sessionToken, etc
		 */

		/* getCurrentUser() will search memory or the disk for a logged in user
		 * that has a valid session. Will return null if the user isn't found
		 * or valid (technically it returns null if any ParseException is
		 * thrown).
		 * Also note that automatic login will have to be manually enabled if
		 * we decide to use it; check ParseUser.java for more info.
		 */

        if(ParseUser.getCurrentUser() != null) {
			/* If there's currently a user logged in, go ahead and run the main
			 * part of the code.
			 */

            startWithCurrentUser();
        }
        else {
			/* Otherwise, attempt to log in as an anonymous user.
			 */
            Intent loginIntent = new Intent(this, LoginActivity.class);
            startActivityForResult(loginIntent, LOGIN_REQUEST_CODE);

        }
        if(mMessages == null){
            if(ParseUser.getCurrentUser() != null) {
                setupMessagePosting();
            }
        }
        mHandler.postDelayed(mRefreshMessagesRunnable, POLL_INTERVAL);



    }

    /* If the back button is pressed while in our nav menu, don't exit the app - close the drawer
    instead.
     */
    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if(drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        }
        else {
            super.onBackPressed();
        }
    }
    /*@Override
    public void onResume(){
        mFirstLoad = true;
    }*/
    /* Inflates the right-hand options menu
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    /* Code that handles when an option menu item is pressed
     */
    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        int id = item.getItemId();
        if(id == R.id.logoutButton) {

            /* this will log the user out and send the app to the login activity
             */
            mAdapter.clear();
            ParseUser.logOut();

            Intent loginIntent = new Intent(this, LoginActivity.class);
            startActivityForResult(loginIntent, LOGIN_REQUEST_CODE);
            //mFirstLoad = true;
            return true;
        }
        else if(id == R.id.action_settings) {
            /* TODO: implement any settings here
             */
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode == LOGIN_REQUEST_CODE){
            if(resultCode == RESULT_OK){
                setupMessagePosting();
            }

        }
    }
    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        /* TODO: code to run based on what navigation bar button was pressed. Note that this
            will probably change based on how we implement the Chats drawer
        */

        /* Code that will close the nav drawer after an item has been selected
         */
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);

        return true;
    }



    /* If we were managing user accounts, this is where the user information
	 * would be populated before moving on.
	 */
    void startWithCurrentUser() {
        setupMessagePosting();
    }


	/* If there isn't a user logged in, log in. In this demo, we're using an
	 * anonymous user here, which is why the class ParseAnonymousUtils is being
	 * called to log a new user in.
	 * See ParseAnonymousUtils.java for more information.
	 */

	/* The anonymous user is created in the background, on a separate thread.
	 * This is why it takes a Callback as a parameter; it will execute the code
	 * in done() once the thread code for logging in the user is complete.
	 */
    void login() {
        ParseAnonymousUtils.logIn(new LogInCallback() {
            @Override
            public void done(ParseUser user, ParseException e) {
                if(e != null) {
                    Log.e(TAG,"Anonymous login failed: ", e);
                }
                else {
                    startWithCurrentUser();
                }
            }
        });
    }

    /* Sets up the button event handler that will post the message to the parse
	 * server. Handles everything needed to send a message to the server.
	 * This code, aside for the code within the onClickListener, should only
	 * ever run once at startup.
	 */
    void setupMessagePosting() {

        /* Find the EditText, Button, and ListView that we defined in
		 * activity_chat.xml. Note that R is a class that contains definitions
		 * for all the resources in this application (for those who aren't
		 * familiar with how Android is structured).
		 */
        etMessage = (EditText) findViewById(R.id.etMessage);
        btSend = (Button) findViewById(R.id.btSend);
        lvChat = (ListView) findViewById(R.id.lvChat);

		/* Create a new array to hold incoming messages.
		 */
        mMessages = new ArrayList<>();

        String userId = ParseUser.getCurrentUser().getObjectId();


        /* Scroll to the bottom when a data set change occurs. 1 will set the
		 * transcript mode of the ListView lvChat to always scroll down to show
		 * new items.
		 */
        lvChat.setTranscriptMode(1);

		/* Since this is the first time the app had loaded, tell
		 * refreshMessages() to focus the list on the very last message,
		 * effectively showing the user the most recent message made in the
		 * chat.
		 */

        mFirstLoad = true;

		/* Get the ID of the currently logged in. ObjectId + className is
		 * a unique identifier for an object in a parse application.
		 */

        /* refresh the userId when setting up the message list to ensure that we are seeing
            the correct user if a logout has occurred
        */
        /*if(ParseUser.getCurrentUser().getObjectId() != null) {
            userId = ParseUser.getCurrentUser().getObjectId();
        }*/
		/* Create an instance of our ChatListAdapter, tie it to the current
		 * context, pass it the userId, and pass it the array of messages.
		 */
        mAdapter = new ChatListAdapter(ChatActivity.this, userId, mMessages);

		/* Set ListView to use our own adapter. The idea of the adapter is that
		 * it is responsible for managing the data within each item in the
		 * ListView.
		 */
        lvChat.setAdapter(mAdapter);


        /* Create message when button is pressed and save it to the remote
		 * Parse DB. Defines what code to run when the send button is pressed.
		 */
        btSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View V) {

				/* Pull the data that was entered in the EditText so that it
				 * can be sent.
				 */
                String data = etMessage.getText().toString();

                /* Make a new instance of our extended ParseObject called
				 * Message, and put the data in it.
				 */
                Message message = new Message();

                /* Fill in data from EditText using the setter function that we
				 * created.
				 */
				/* This would be a good place to encipher the outgoing message.
				 */
                message.setBody(data);

                /* Same story for the userId.
				 */
                message.setUserId(ParseUser.getCurrentUser().getObjectId());

				/* save the message to the server. Note that this is done on
				 * a separate thread, just like the user login. That's why
				 * a callback must be passed to the function.
				 * Note that this is a function that was inherited from
				 * ParseObject.
				 */
                message.saveInBackground(new SaveCallback() {
                    @Override
                    public void done(ParseException e) {
                        if(e == null) {
							/* This just notifies us that the message was
							 * successfully saved via on-screen message.
							 * Completely unnecessary for the function of the
							 * app.
							 */
                            Toast.makeText(ChatActivity.this, "Successfully sent message to DB", Toast.LENGTH_SHORT).show();
                        }
                        else {
                            Log.e(TAG, "Failed to send message", e);
                        }
                    }
                });

				/* Set the EditText where the user types in their message back
				 * to being empty.
				 */
                etMessage.setText(null);
            }
        });


    }

    /* Get messages from Parse and load them into the ListAdapter. Currently,
	 * this function is being run every second due to the Runnable
	 * mRefreshMessagesRunnable that was run earlier.
	 */
    void refreshMessages() {
        if (ParseUser.getCurrentUser() != null){
		/* Create a new ParseQuery object: this will manage querying the
		 * backend database. Because ParseObjects represent data stored both
		 * locally and remotely, we will also use our Messages subclass
		 * to define the data. getQuery() will take in a class literal and
		 * create a query for it, to pull matching objects from the DB.
		 * Note that since getQuery() is not given additional parameters it
		 * will by default fetch all instances of Message objects it finds
		 * on the server.
		 */
        ParseQuery<Message> query = ParseQuery.getQuery(Message.class);

        /* Limit how many results are returned from the query. By default this
		 * limit is 1000; setting to a negative number will remove the limit.
		 */
        query.setLimit(MAX_MESSAGES_TO_SHOW);

		/* Sort the results fetched. The key given, "createdAt", is something
		 * that our Message subclass inherits from ParseObject.
		 */
        query.orderByDescending("createdAt");

		/* Run the ParseQuery we made in the background, just like when we
		 * save the messages to the server.
		 * A FindCallback is an interface that Parse uses specifically for code
		 * that is to be executed after a query runs and pulls objects back
		 * from the server.
		 */
        query.findInBackground(new FindCallback<Message>() {
           public void done(List<Message> messages, ParseException e) {
               if(e == null) {

					/* Clear the current array of Message objects, presumably
					 * full of objects from the last query.
					 */
                   //setupMessagePosting();
                   mMessages.clear();

					/* Reverse the elements provided. Honestly, i'm not sure
					 * why the example code orders the results by descending
					 * order only to flip them here... Querying for them in
					 * ascending order is functionally the same, but I'm keeping
					 * the example code intact for now in case it's relevant
					 * for a reason beyond my comprehension.
					 */
					Collections.reverse(messages);

					/* Add all the messages to our array of Message objects.
					 */
					mMessages.addAll(messages);

					/* Tell our ListView adapter that the data it's tied to
					 * has changed.
					 */
					mAdapter.notifyDataSetChanged();

					/* If this is the first time the query has run, set the
					 * selected item in the ListView to the very last one.
					 * So, when the app is opened for the first time and
					 * all the messages are fetched, it will select the most
					 * recent one and scroll to it automatically.
					 */
                   if(mFirstLoad) {
                       lvChat.setSelection(mAdapter.getCount() - 1);
                       mFirstLoad = false;
                   }
               }
               else {
                   Log.e("Message", "Error Loading Messages" + e);
               }
           }
        });
    }}


}
