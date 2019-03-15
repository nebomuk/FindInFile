package com.taiko.findinfile;

import android.app.Activity;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.Window;
import android.widget.SearchView;
import android.widget.ShareActionProvider;
import android.widget.TextView;
import android.widget.Toast;

import com.melnykov.fab.FloatingActionButton;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;


/**
 *  copies the text to the clipboard from the specified seach string position onwards
 */

public class MainActivity extends Activity {

    private static final String PREF_KEY_URI = "pref_key_uri";
    private final String TAG = this.getClass().getName();

    private static final int READ_REQUEST_CODE = 0xD0C;
    private TextView mTextView;
    private StringBuilder mStringBuilder = new StringBuilder("");
    private ShareActionProvider mShareActionProvider;
    private String mSearchStr = "";
    private String mTextFromSearchQuery = "";
    private SearchView mSearchView;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_PROGRESS);
        setContentView(R.layout.activity_main);

        makeActionOverflowMenuShown();

        Intent intent = getIntent();
        handleIntent(intent); // aso called in onNewIntent


        mTextView = findViewById(R.id.textView);
        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                // ACTION_OPEN_DOCUMENT is the intent to choose a file via the system's file
                // browser.
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);

                // Filter to only show results that can be "opened", such as a
                // file (as opposed to a list of contacts or timezones)
                intent.addCategory(Intent.CATEGORY_OPENABLE);

                intent.setType("*/*");

                startActivityForResult(intent, READ_REQUEST_CODE);
            }});


    }

    @Override
    protected void onStart()
    {
        super.onStart();
        // attempt to load last opened file
        SharedPreferences preferences = getPreferences(Context.MODE_PRIVATE);
        String uriStr = preferences.getString(PREF_KEY_URI,"");
        if(!TextUtils.isEmpty(uriStr))
        {
            loadUri(Uri.parse(uriStr));
        }
    }

    /**
     *  called when activity is already started
     */
    @Override
    public void onNewIntent(Intent intent)
    {
        handleIntent(intent);

        // call this again because onCreateOptionsMenu is not called again
        if(mSearchView != null && !TextUtils.isEmpty(mSearchStr))
            mSearchView.setQuery(mSearchStr, true);
    }

    private void handleIntent(Intent intent)
    {
        // Get action and MIME type
        String action = intent.getAction();
        String type = intent.getType();
        if (Intent.ACTION_SEND.equals(action) && type != null) {
            if ("text/plain".equals(type)) {
                mSearchStr = intent.getStringExtra(Intent.EXTRA_TEXT);
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode,
                                 Intent resultData) {

        // The ACTION_OPEN_DOCUMENT intent was sent with the request code
        // READ_REQUEST_CODE. If the request code seen here doesn't match, it's the
        // response to some other intent, and the code below shouldn't run at all.

        if (resultData == null)
        {
            return;
        }

        if (requestCode == READ_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            Uri uri = resultData.getData();

            int takeFlags = resultData.getFlags()
                    & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

            //noinspection WrongConstant
            getContentResolver().takePersistableUriPermission(uri, takeFlags);

            SharedPreferences preferences = this.getPreferences(Context.MODE_PRIVATE);
            preferences.edit().putString(PREF_KEY_URI,uri.toString()).apply();
            loadUri(uri);

        }
    }

    private void loadUri(final Uri uri)
    {
        // The document selected by the user won't be returned in the intent.
        // Instead, a URI to that document will be contained in the return intent
        // provided to this method as a parameter.
        // Pull that URI using resultData.getData().

        Log.i(TAG, "Uri: " + uri.toString());

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
                    mStringBuilder = new StringBuilder(inputStream.available());


                    BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
                    String line;

                    while ((line = br.readLine()) != null) {
                        mStringBuilder.append(line);
                        mStringBuilder.append('\n');
                    }
                    br.close();
                    if(mSearchView != null) // null when onCreateOptionsMenu has not been called yet
                    {
                        mTextFromSearchQuery = createTextFromSearchQuery(mSearchView.getQuery().toString());

                    }
                    else
                    {
                        mTextFromSearchQuery = mStringBuilder.toString();
                    }

                }
                catch (final Exception e) {
                    runOnUiThread(new Runnable()
                                  {
                                      @Override
                                      public void run()
                                      {
                                          Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
                                      }
                                  });
                }

                // add the span to the text editor as soon as it is created
                runOnUiThread(new Runnable(){

                    public void run()
                    {
                        CharSequence toastText = "text file loaded";
                        int duration = Toast.LENGTH_SHORT;
                        Toast toast = Toast.makeText(MainActivity.this.getApplicationContext(), toastText, duration);
                        toast.show();
                        setProgress(Window.PROGRESS_END);
                        mHandler.removeCallbacks(mProgressRunner);

                        displayText(mSearchView.getQuery().toString());

                    }
                });
            }
        }.start();
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
                String truncatedText = truncateText(mTextFromSearchQuery, shareMaxTextLength);

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

                String truncatedText = truncateText(mTextFromSearchQuery,maxTextLength);
                android.content.ClipData clip = android.content.ClipData.newPlainText("Copied Text", truncatedText);
                clipboard.setPrimaryClip(clip);
                Context context = getApplicationContext();

                CharSequence toastText = "text copied to clipboard " + mTextFromSearchQuery.length();
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
            mSearchView = (SearchView) menu.findItem(R.id.menu_search)
                    .getActionView();
            mSearchView.setSearchableInfo(searchManager
                    .getSearchableInfo(getComponentName()));
            mSearchView.setIconifiedByDefault(false);

            if(mSearchStr != null && mSearchStr.length() > 0)
            mSearchView.setQuery(mSearchStr, true);

        mSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            public boolean onQueryTextChange(String newText) {
                onQueryTextSubmit(newText);
                return true;
            }

            public boolean onQueryTextSubmit(String query) {

                mTextFromSearchQuery =  createTextFromSearchQuery(query);
                displayText(query);

                return true;
            }
        });

        return super.onCreateOptionsMenu(menu);
    }

    // initializes mTextFromSearchQuery variable
    private String createTextFromSearchQuery(String query)
    {
        if (!TextUtils.isEmpty(mStringBuilder) && (query.length() < 1 ||  mStringBuilder.length() < 1))
        {
            return mStringBuilder.toString();

        }

        int index = mStringBuilder.indexOf(query);
        if (index <1 || index > mStringBuilder.length()-1) {
            return mStringBuilder.toString();
        }
        else {
            return mStringBuilder.substring(index);
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
    private void displayText(CharSequence highlight)
    {
        if( mTextFromSearchQuery == null || mTextFromSearchQuery.length() <1)
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
        int maxLineCount = screenHeight/ mTextView.getLineHeight();
        mTextView.setLines(maxLineCount);

        // create a reference of  the text from the given index onwards

        while ((lineCount < maxLineCount) && (numChars <  mTextFromSearchQuery.length())) {
            numChars = numChars + paint.breakText( mTextFromSearchQuery.substring(numChars, numChars + maxLineLength), true, screenWidth, null);
            lineCount ++;
        }

        // retrieve the String to be displayed in the current textbox
        String toBeDisplayed =  mTextFromSearchQuery.substring(0, numChars);

        if(highlight != null && highlight.length() >1) {
            Spannable spannable = new SpannableString(toBeDisplayed);
            spannable.setSpan(new ForegroundColorSpan(Color.BLUE), 0, highlight.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            mTextView.setText(spannable);
        }
        else
        {
            mTextView.setText(toBeDisplayed);
        }
    }

    //devices with hardware menu button (e.g. Samsung Galaxy S4) don't show action overflow menu
    private void makeActionOverflowMenuShown()
    {
        try {
            ViewConfiguration config = ViewConfiguration.get(this);
            Field menuKeyField = ViewConfiguration.class.getDeclaredField("sHasPermanentMenuKey");
            if (menuKeyField != null) {
                menuKeyField.setAccessible(true);
                menuKeyField.setBoolean(config, false);
            }
        } catch (Exception e) {
        }
    }
}
