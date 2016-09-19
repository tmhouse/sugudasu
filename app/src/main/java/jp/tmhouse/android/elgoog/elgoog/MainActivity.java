package jp.tmhouse.android.elgoog.elgoog;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    private WebView m_webview;
    private EditText m_url;
    private EditText m_searchText;
    private Button      m_go;
    private Button      m_clear;
    private TmContinuousSpeechRecognizer  m_csr;
    private TextFinder m_textFinder = new TextFinder();
    private Beeper      m_beeper = new Beeper();
    private Prefs       m_prefs;
    private RadioGroup  m_inputTypeRadioGrp;
    private FloatingActionButton m_speekNowBtn;

    private static final int c_DIALOG_CLEAR_HIST = 1;

    /**
     * 文字列配列のどれかをwebviewのページ内から探してhiglightする.
     */
    private class TextFinder {
        private ArrayList<String> m_lastSpeechTextArray;
        private int m_doFindTextArrayCount = 0;
        private String m_curFindText = null;

        private void doFindTextArray(ArrayList<String> arr) {
            // trimしよう
            ArrayList<String> trimedArr = new ArrayList<String>(20);
            int size = arr.size();
            for( int i = 0; i < size; i++ ) {
                String org = arr.get(i);
                String trimed = org.trim().replace(" ", "").replace("　", "");
                if( ! org.equals(trimed) ) {
                    trimedArr.add(trimed);
                    Log.d("doFindTextArray", "trimed added. org=" + org + ", trimed=" + trimed);
                } else {
                    Log.d("doFindTextArray", "trimed not added. org=" + org);
                }
            }
            arr.addAll(trimedArr);
            m_lastSpeechTextArray = arr;
            m_doFindTextArrayCount = 0;
            findNextText();
        }
        private void doFindText(String str) {
            if( str == null || str.isEmpty() ) {
                return;
            }
            ArrayList<String> arr = new ArrayList<String>(1);
            arr.add(str);
            doFindTextArray(arr);
        }

        private String getCurrentText() {
            return(m_curFindText);
        }

        private void stopFindText() {
            m_curFindText = null;
            m_lastSpeechTextArray = null;
            m_doFindTextArrayCount = 0;
        }

        private void findNextText() {
            if (m_lastSpeechTextArray == null) {
                return;
            }
            try {
                m_curFindText = m_lastSpeechTextArray.get(m_doFindTextArrayCount);
                if( m_curFindText == null || m_curFindText.isEmpty() ) {
                    throw new RuntimeException("why find null string?");
                }
                m_webview.findAllAsync(m_curFindText);
            } catch (IndexOutOfBoundsException e) {
                // end
                Log.w("findNextText", "all text were not found");
                String msg = null;
                if( m_lastSpeechTextArray.size() > 1 ) {
                    msg = concatArrText() + "\nは全部見つかりません。";
                } else {
                    msg = concatArrText() + "\nは見つかりません。";
                }
                Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();
                stopFindText();
                m_beeper.beep();
                return;
            }
            m_doFindTextArrayCount++;
        }

        private String concatArrText() {
            StringBuilder sb = new StringBuilder();
            int len = m_lastSpeechTextArray.size();
            for( int i = 0; i < len; i++ ) {
                sb.append("- ");
                String s = m_lastSpeechTextArray.get(i);
                sb.append(s);
                if( i < len ) {
                    sb.append("\n");
                }
            }
            return(sb.toString());
        }
    }

    private void updateNaviBtn() {
        Button backBtn = (Button)findViewById(R.id.back);
        Button nextBtn = (Button)findViewById(R.id.next);
        if( !m_webview.canGoBack() ) {
            backBtn.setEnabled(false);
        } else {
            backBtn.setEnabled(true);
        }
        if( !m_webview.canGoForward() ) {
            nextBtn.setEnabled(false);
        } else {
            nextBtn.setEnabled(true);
        }
        //Log.i("updateNaviBtn", "updateNaviBtn ignored");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.e("app", "test");
        m_prefs = new Prefs(this);
        m_csr = new TmContinuousSpeechRecognizer(this);
        m_csr.setOnResultListener(new TmContinuousSpeechRecognizer.OnRecognizedCB() {
            @Override
            public void onRecognized(ArrayList<String> results) {
                m_textFinder.doFindTextArray(results);
            }
        });

        final Button homeBtn = (Button)findViewById(R.id.home);
        homeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loadUrl("http://www.google.com");
            }
        });

        final Button clearHistBtn = (Button)findViewById(R.id.clearHist);
        clearHistBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDialog(c_DIALOG_CLEAR_HIST);
            }
        });

        final Button reloadBtn = (Button)findViewById(R.id.reload);
        reloadBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                m_webview.reload();
            }
        });

        final Button backBtn = (Button)findViewById(R.id.back);
        final Button nextBtn = (Button)findViewById(R.id.next);
        backBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                m_webview.goBack();
                updateNaviBtn();
            }
        });
        nextBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                m_webview.goForward();
                updateNaviBtn();
            }
        });

        m_speekNowBtn = (FloatingActionButton)findViewById(R.id.speekNowBtn);
        final ColorStateList fireColor =
                ColorStateList.valueOf(getResources().getColor(R.color.colorAccent));
        final ColorStateList normalColor = m_speekNowBtn.getBackgroundTintList();

        m_speekNowBtn.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                int action = event.getAction();
                //Log.d("mic onTouch", "action=" + event.getAction());
                if( action == MotionEvent.ACTION_DOWN ) {
                    m_speekNowBtn.setBackgroundTintList(fireColor);
                    m_csr.startListening();
                } else if( action == MotionEvent.ACTION_UP ) {
                    m_speekNowBtn.setBackgroundTintList(normalColor);
                    m_csr.stopListening();
                }
                return true;
            }
        });

        m_url = (EditText) findViewById(R.id.url);

        m_searchText = (EditText) findViewById(R.id.searchText);
        m_searchText.addTextChangedListener(m_textWatcher);
        m_searchText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if( hasFocus == false ) {
                }
            }
        });
        m_searchText.requestFocus();

        m_go = (Button) findViewById(R.id.go);
        m_go.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                m_webview.loadUrl(m_url.getText().toString());
            }
        });

        m_clear = (Button)findViewById(R.id.clear);
        m_clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setFindTextView("", false);
            }
        });

        setInputMode(m_prefs.getInputMode());

        m_inputTypeRadioGrp = (RadioGroup)findViewById(R.id.inputTypeRadioGrp);
        m_inputTypeRadioGrp.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                int textModeId = ((RadioButton)findViewById(R.id.inputText)).getId();
                if( textModeId == checkedId ) {
                    setInputMode(InputType.TYPE_CLASS_TEXT);
                } else {
                    setInputMode(InputType.TYPE_CLASS_NUMBER);
                }
            }
        });

        m_webview = (WebView)findViewById(R.id.webView);
        m_webview.setFindListener(new WebView.FindListener() {
            @Override
            public void onFindResultReceived(
                    int activeMatchOrdinal, int numberOfMatches, boolean isDoneCounting) {
                String curText = m_textFinder.getCurrentText();
                Log.i("app", "curText=" + curText +
                        ", activeMatchOrdinal=" + activeMatchOrdinal +
                        ", numberOfMatches=" + numberOfMatches +
                        ", isDoneCounting=" + Boolean.toString(isDoneCounting));
                if( isDoneCounting && (curText != null) ) {
                    if( numberOfMatches > 0 ) {
                        Log.i("find text", "found text:" + curText);
                        m_textFinder.stopFindText();
                        setFindTextView(curText, false);
                    } else {
                        Log.i("find text", "not found:" + curText);
                        m_textFinder.findNextText();
                    }
                }
            }
        });
        init(m_webview);

        // historyなどの復帰
        Bundle b = savedInstanceState;
        if( b == null ) {
            if( ! m_prefs.restoreWebState(m_webview) ) {
                loadUrl("http://www.google.com");
            }
        }

        updateNaviBtn();
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        switch( id ) {
            case c_DIALOG_CLEAR_HIST:
                builder.setTitle("Clear history?")
                        .setMessage("OK to clear history.")
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                // OK button pressed
                                m_prefs.clearWebState();
                                m_webview.clearHistory();
                                m_webview.clearCache(true);
                                updateNaviBtn();
                            }
                        })
                        .setNegativeButton("Cancel", null);
                break;
            default:
                throw new RuntimeException("unknown dialog id");
        }
        return builder.create();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        //m_webview.saveState(outState);
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        //m_webview.restoreState(savedInstanceState);
    }
    @Override
    protected void onDestroy() {
        m_csr.destroy();

        m_prefs.saveWebState(m_webview);

        // WebView.destroy() called while WebView is still attached to window.
        // と言われるため、まず親子切り離し
        ViewParent vp = m_webview.getParent();
        if( vp instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup)vp;
            vg.removeAllViews();
        }
        //m_webview.removeAllViews();
        m_webview.destroy();

        super.onDestroy();
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }


    @Override
    public void onResume() {
        super.onResume();
        m_webview.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        m_webview.onPause();
    }

    private void setInputMode(int mode) {
        if( mode == InputType.TYPE_CLASS_NUMBER) {
            ((RadioButton)findViewById(R.id.inputNumeric)).setChecked(true);
            m_searchText.setInputType(InputType.TYPE_CLASS_NUMBER);
        } else if( mode == InputType.TYPE_CLASS_TEXT) {
            ((RadioButton)findViewById(R.id.inputText)).setChecked(true);
            m_searchText.setInputType(InputType.TYPE_CLASS_TEXT);
        }
        m_prefs.saveInputMode(mode);
    }

    private void setFindTextView(String str, boolean fireTextWatcher) {
        String cur = m_searchText.getText().toString();
        if( cur != null && cur.equals(str) ) {
            return;
        }

        if( fireTextWatcher ) {
            m_searchText.setText(str);
        } else {
            m_searchText.removeTextChangedListener(m_textWatcher);
            m_searchText.setText(str);
            m_searchText.setSelection(0, str.length());
            m_searchText.addTextChangedListener(m_textWatcher);
        }
    }

    private TextWatcher m_textWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            Log.d("afterTextChanged", "find text:" + s.toString());
            m_textFinder.doFindText(s.toString());
        }
    };

    private void updateProgressBar(int val) {
        ProgressBar pbar = (ProgressBar)findViewById(R.id.progressBar);
        if( val >= pbar.getMax() ) {
            pbar.setVisibility(View.GONE);
        } else {
            if (pbar.getVisibility() == View.GONE) {
                pbar.setVisibility(View.VISIBLE);
            }
            pbar.setProgress(val);
        }
    }

    private void init(WebView webview) {
        final Activity activity = this;
        webview.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int progress) {
                // Activities and WebViews measure progress with different scales.
                // The progress meter will automatically disappear when we reach 100%
                //activity.setProgress(progress * 1000);
                Log.d("progress", "progress=" + progress);
                updateProgressBar(progress);
            }
            @Override
            public void onReceivedTitle(WebView view, String title) {
                TextView pageTitle = (TextView)findViewById(R.id.pageTitle);
                pageTitle.setText(title);
            }

            @Override
            public void onReceivedIcon(WebView view, Bitmap icon) {
                ImageView pageIcon = (ImageView)findViewById(R.id.pageIcon);
                pageIcon.setImageBitmap(icon);
            }
        });
        webview.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                Toast.makeText(activity, "Oh no! " + description, Toast.LENGTH_SHORT).show();
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String request) {
                Log.d("url loading", "url=" + request);
                ImageView pageIcon = (ImageView)findViewById(R.id.pageIcon);
                pageIcon.setImageBitmap(null);
                TextView pageTitle = (TextView)findViewById(R.id.pageTitle);
                pageTitle.setText("Loading...");
                return false;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                if( ! m_url.getText().toString().equals(url) ) {
                    m_url.setText(url);
                }
                // ここよりonReceivedTitleの方が先に来るからLoading中を示すには別の方法が必要
                //TextView pageTitle = (TextView)findViewById(R.id.pageTitle);
                //pageTitle.setText("Loading...");
                //ImageView pageIcon = (ImageView)findViewById(R.id.pageIcon);
                //pageIcon.setImageDrawable(null);
                updateNaviBtn();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if( ! m_url.getText().toString().equals(url) ) {
                    m_url.setText(url);
                }
                updateNaviBtn();
            }
        });

        webview.getSettings().setJavaScriptEnabled(true);
        webview.getSettings().setDomStorageEnabled(true);
        webview.getSettings().setBuiltInZoomControls(true);
        webview.getSettings().setDisplayZoomControls(false);

        // ピンチイン・アウトできるようにし、改行位置を変更しない
        webview.getSettings().setLoadWithOverviewMode(true);
        webview.getSettings().setUseWideViewPort(true);

        // PCモード
        String newUA= "Mozilla/5.0 (X11; U; Linux i686; en-US; rv:1.9.0.4) Gecko/20100101 Firefox/4.0";
        webview.getSettings().setUserAgentString(newUA);
    }

    private void loadUrl(String url) {
        if( !( m_url.getText() != null && m_url.getText().equals(url)) ) {
            m_url.setText(url);
        }
        m_webview.loadUrl(url);
    }


}
