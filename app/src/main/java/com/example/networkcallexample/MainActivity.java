package com.example.networkcallexample;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

public class MainActivity extends AppCompatActivity {
    private ConnectivityManager mConnMgr;
    private HttpURLConnection mConn;
    private InputStream mInputStream;
    private TextView mTv;
    private Thread mNetworkThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mTv = findViewById(R.id.tv);

        //STEP 1: Check the network connection state
        // Let's connect to a url...so how do?
        //first we make sure we have the proper permissions
        //next, we use the ConnectivityManager and NetworkInfo objects to
        //check the status of our network (new thread not necessary)
        //then we perform our task if everything is good to go
        mConnMgr = (ConnectivityManager)
                getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo networkInfo = mConnMgr.getActiveNetworkInfo();
        //what if i wanted to check if connected to Wifi?
        boolean isWifi = mConnMgr.getNetworkInfo(
                ConnectivityManager.TYPE_WIFI).isConnected();
        boolean isMobile = mConnMgr.getNetworkInfo(
                ConnectivityManager.TYPE_MOBILE).isConnected();

        if (networkInfo != null && networkInfo.isConnected()) {
            // STEP 2: Create background thread to connect and get data.
            /**
             Generally, Retrofit or RxJava would be used to handle concurrency,
            or even Threadpool Executor, HandlerThread, WorkManager (all part
            of the Java/Android Libraries), or the Concurrency library
            from the Java.util package. In class, I'll show an example
            of Threadpool Executors being used, and we will use
            Retrofit and RxJava (separately, and combined) to see
            how they work.
            For more information on threadpool executors, and how they're
            used with the Activity lifecycle, please take a look at
            these articles:
            https://medium.com/@frank.tan/using-a-thread-pool-in-android-e3c88f59d07f
            and
            https://blog.mindorks.com/threadpoolexecutor-in-android-8e9d22330ee3
            */

            //here we are using just a thread to schedule the network
            //call, but as we'll see later, THIS IS NOT A GOOD PRACTICE.
            //Instead, use one of the libraries mentioned above, and
            //make sure to account for the activity lifecycle
            mNetworkThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        //STEP 3A: build out your URL; we'll use the url
                        //for our github api; of course, this step should
                        //be done in a try-catch block
                        URL myUrl = buildURL("archow",
                                "repositories");

                        //STEP 3B: perform the network call by opening
                        //a url connection
                        String responseString = performNetworkCall(myUrl);

                        //STEP 4: Parse the results retrieved from the string
                        //here you have to know what the API response looks like
                        if (!responseString.isEmpty()) {
                            parseStringToJsonObject(responseString);
                        }

                    } catch (
                            //exception for building URL
                            MalformedURLException m
                    ) {
                        m.printStackTrace();
                    }
                }
            });



        } else {
            mTv.setText("No network connection available.");
        }
    }

    private URL buildURL(String searchQuery, String sortBy)
            throws MalformedURLException {
        //(in this case, I am getting the base url and query paramters
        //from the constants file i created)
        Uri builtUri = Uri.parse(Constants.BASE_URL).buildUpon()
                .appendQueryParameter(Constants.SEARCH_PARAM, searchQuery)
                .appendQueryParameter(Constants.SORT_BY_PARAM, sortBy)
                .build();
        return new URL(builtUri.toString());
    }

    //this network call will be done inside a thread
    private String performNetworkCall(URL someURL) {
        String resultString = "";
        try {
            //open url connection
            mConn =
                    (HttpURLConnection) someURL.openConnection();

            //configure the connection
            mConn.setReadTimeout(10000 /* milliseconds */);
            mConn.setConnectTimeout(15000 /* milliseconds */);
            mConn.setRequestMethod("GET");
            mConn.setDoInput(true);

            //connect and get response
            mConn.connect();
            //(this is just the response code, like 404, 200, 400, etc.)
            int responseCode = mConn.getResponseCode();
            Log.d("debug_tag", "The response is: " + responseCode);

            //STEP 3C: Open up an Input Stream, and then parse its results
            mInputStream = mConn.getInputStream();
            resultString = convertInputToString(mInputStream);
        } catch (IOException i) {
            i.printStackTrace();
        } finally {
            if (mConn != null) {
                mConn.disconnect();
            }
            if (mInputStream != null) {
                try {
                    mInputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return resultString;
    }

    //method to convert an inputStream to a String
    public String convertInputToString(InputStream stream)
            throws IOException {
        StringBuilder builder = new StringBuilder();
        BufferedReader reader =
                new BufferedReader(new InputStreamReader(stream));
        String line;
        while ((line = reader.readLine()) != null) {
            builder.append(line).append("\n");
        }
        if (builder.length() == 0) {
            return "";
        }
        return builder.toString();
    }

    public void parseStringToJsonObject(String jSONString) {
        try {
            //we basically take the entire string and convert it
            //to a json object.
            JSONObject jsonObject = new JSONObject(jSONString);

            //then we grab each item in the object like so:
            int totalCount = (int) jsonObject.get("total_count");
            boolean incompleteResults = (boolean) jsonObject.get("incomplete_results");

            //if we want to get an array of objects, we use JSONArray,
            //then we can iterate through the array and convert each of those
            //objects as necessary
            JSONArray items = (JSONArray) jsonObject.get("items");
            for (int i=0; i<items.length(); i++) {
                JSONObject currentObject = (JSONObject) items.get(i);
                String login = (String) currentObject.get("login");
                Log.d("login_name_of_item", login);
            }
        } catch (JSONException j) {
            j.printStackTrace();
        }

    }

    //Disconnect any connection if it exists, and stop any thread
    @Override
    protected void onDestroy() {
        if (mConn != null) {
            mConn.disconnect();
        }
        if (mNetworkThread != null) {
            //this is deprecated for a reason; just plain unsafe
            //hence there are better alternatives to thread handling
            mNetworkThread.stop();
        }
        super.onDestroy();
    }
}