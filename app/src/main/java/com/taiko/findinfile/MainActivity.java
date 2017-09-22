package com.taiko.findinfile;

import android.app.Activity;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.SearchView;
import android.widget.ShareActionProvider;
import android.widget.TextView;
import android.widget.Toast;

import com.melnykov.fab.FloatingActionButton;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;


/**
 *  copies the text to the clipboard from the specified seach string position onwards
 */

public class MainActivity extends Activity {

    private static final int READ_REQUEST_CODE = 0xD0C;
    private TextView textView;
    private StringBuilder text = new StringBuilder("");
    private ShareActionProvider mShareActionProvider;
    private String searchStr;
    private String textFromSearchQuery;
    private SearchView searchView;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_PROGRESS);
        setContentView(R.layout.activity_main);


        Intent intent = getIntent();
        handleIntent(intent); // aso called in onNewIntent


        textView = findViewById(R.id.textView);
        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                // ACTION_OPEN_DOCUMENT is the intent to choose a file via the system's file
                // browser.
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);

                // Filter to only show results that can be "opened", such as a
                // file (as opposed to a list of contacts or timezones)
                intent.addCategory(Intent.CATEGORY_OPENABLE);

                intent.setType("*/*");

                startActivityForResult(intent, READ_REQUEST_CODE);
            }});
    }

    /**
     *  called when activity is already started
     */
    @Override
    public void onNewIntent(Intent intent)
    {
        handleIntent(intent);

        // call this again because onCreateOptionsMenu is not called again
        if(searchView != null && searchStr != null && searchStr.length() > 0)
            searchView.setQuery(searchStr, true);
    }

    private void handleIntent(Intent intent)
    {
        // Get action and MIME type
        String action = intent.getAction();
        String type = intent.getType();
        if (Intent.ACTION_SEND.equals(action) && type != null) {
            if ("text/plain".equals(type)) {
                searchStr = intent.getStringExtra(Intent.EXTRA_TEXT);
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode,
                                 Intent resultData) {

        // The ACTION_OPEN_DOCUMENT intent was sent with the request code
        // READ_REQUEST_CODE. If the request code seen here doesn't match, it's the
        // response to some other intent, and the code below shouldn't run at all.

        if (requestCode == READ_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            // The document selected by the user won't be returned in the intent.
            // Instead, a URI to that document will be contained in the return intent
            // provided to this method as a parameter.
            // Pull that URI using resultData.getData().
            final Uri uri;
            if (resultData != null) {
                uri = resultData.getData();
                Log.i("TESTTAG", "Uri: " + uri.toString());

                // simple timer controlled progress reporting
                final Handler mHandler = new Handler();
                final Runnable mProgressRunner = new Runnable() {
                    private int mProgress;
                    public void run() {
                        mProgress += 8;

                        //Normalize our progress along the progress bar's scale
                        int progress = (Window.PROGRESS_END - Window.PROGRESS_START) / 100 * mProgress;
                        setProgress(progress);

                        if (mProgress < 100) {
                            mHandler.postDelayed(this, 10);
                        }
                    }};
                mProgressRunner.run();


                // load file contents and parse html thread
                new Thread(){
                    public void run()
                    {
                        try {
                            InputStream inputStream = MainActivity.this.getContentResolver().openInputStream(uri);
                         text = new StringBuilder(inputStream.available());


                            BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
                            String line;

                            while ((line = br.readLine()) != null) {
                                text.append(line);
                                text.append('\n');
                            }
                            br.close();
                            createTextFromSearchQuery(searchView.getQuery().toString());
                        }
                        catch (IOException e) {
                            text.append(e.getMessage());
                            //You'll need to add proper error handling here
                        }

                        // add the span to the text editor as soon as it is created
                        MainActivity.this.runOnUiThread(new Runnable(){

                            public void run()
                            {
                                CharSequence toastText = "text file loaded";
                                int duration = Toast.LENGTH_SHORT;
                                Toast toast = Toast.makeText(MainActivity.this.getApplicationContext(), toastText, duration);
                                toast.show();
                                setProgress(Window.PROGRESS_END);
                                mHandler.removeCallbacks(mProgressRunner);

                                displayText(searchView.getQuery().toString());
                            }
                        });
                    }
                }.start();
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {



        // Handle presses on the action bar items
        switch (item.getItemId()) {

            // when sharing fails, the max intent extra size has been exceeded (~ 500kb)
            // max intent extra sizes https://www.neotechsoftware.com/blog/android-intent-size-limit

            case R.id.menu_item_share:
            {
                Intent sendIntent = new Intent();
                sendIntent.setAction(Intent.ACTION_SEND);


                int shareMaxTextLength = 100000; // this number has been determined by trial and error
                String truncatedText = truncateText(textFromSearchQuery, shareMaxTextLength);

                sendIntent.putExtra(Intent.EXTRA_TEXT, truncatedText);
                sendIntent.setType("text/plain");
                if (mShareActionProvider != null)
                    mShareActionProvider.setShareIntent(sendIntent);
                return true;
            }
            case R.id.menu_item_copy_content:
            {
                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);

                // pleco reader supports little more than 1000 pages of clipboard text, the number 448486 is suitable for nexus screen sizes, on tablets probably more chars are allowed
                final int maxTextLength = 448486;

                String truncatedText = truncateText(textFromSearchQuery,maxTextLength);
                android.content.ClipData clip = android.content.ClipData.newPlainText("Copied Text", truncatedText);
                clipboard.setPrimaryClip(clip);
                Context context = getApplicationContext();

                CharSequence toastText = "text copied to clipboard " + textFromSearchQuery.length();
                int duration = Toast.LENGTH_SHORT;
                Toast toast = Toast.makeText(context, toastText, duration);
                toast.show();
                return true;
            }
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
        {
        getMenuInflater().inflate(R.menu.menu_main, menu);

        MenuItem item = menu.findItem(R.id.menu_item_share);
        mShareActionProvider = (ShareActionProvider) item.getActionProvider();

        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
            searchView = (SearchView) menu.findItem(R.id.menu_search)
                    .getActionView();
            searchView.setSearchableInfo(searchManager
                    .getSearchableInfo(getComponentName()));
            searchView.setIconifiedByDefault(false);

            if(searchStr != null && searchStr.length() > 0)
            searchView.setQuery(searchStr, true);

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            public boolean onQueryTextChange(String newText) {
                onQueryTextSubmit(newText);
                return true;
            }

            public boolean onQueryTextSubmit(String query) {

                createTextFromSearchQuery(query);
                displayText(query);

                return true;
            }
        });

        return super.onCreateOptionsMenu(menu);
    }

    // initializes textFromSearchQuery variable
    private void createTextFromSearchQuery(String query)
    {
        if (query.length() < 1 || text == null || text.length() < 1) {
            textFromSearchQuery = text.toString();
            return;
        }

        int index = text.indexOf(query);
        if (index <1 || index > text.length()-1) {
            textFromSearchQuery = text.toString();
        }
        else {
            textFromSearchQuery = text.substring(index);
        }

    }

    private static String truncateText(String srcText,int maxLength)
    {
        final int length = srcText.length();
        return length - 1 > maxLength ? srcText.substring(0,maxLength) : srcText;
    }

    /**
     *  returns a reference to the given StringBuilder str from the given String position on
     */
    protected void displayText(CharSequence highlight)
    {
        if( textFromSearchQuery == null || textFromSearchQuery.length() <1)
            return;

        // display part of the text

        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);
        int screenWidth = dm.widthPixels;
        int screenHeight= dm.heightPixels;

        Paint paint = new Paint();

        int numChars = 0;
        int lineCount = 0;
        int maxLineLength = 512; // used for the breakText substring because it will be slow if it is the complete string
        int maxLineCount = screenHeight/textView.getLineHeight();
        textView.setLines(maxLineCount);

        // create a reference of  the text from the given index onwards

        while ((lineCount < maxLineCount) && (numChars <  textFromSearchQuery.length())) {
            numChars = numChars + paint.breakText( textFromSearchQuery.substring(numChars, numChars + maxLineLength), true, screenWidth, null);
            lineCount ++;
        }

        // retrieve the String to be displayed in the current textbox
        String toBeDisplayed =  textFromSearchQuery.substring(0, numChars);

        if(highlight != null || highlight.length() <1) {
            Spannable spannable = new SpannableString(toBeDisplayed);
            spannable.setSpan(new ForegroundColorSpan(Color.BLUE), 0, highlight.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            textView.setText(spannable);
        }
        else
        {
            textView.setText(toBeDisplayed);
        }
    }
}
