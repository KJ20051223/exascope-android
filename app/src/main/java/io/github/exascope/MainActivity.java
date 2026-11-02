package io.github.exascope;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int QUERY_ID = 1001, KEY_ID = 1002, SCOPE_ID = 1003, TEMPLATE_ID = 1004;
    private final Handler main = new Handler(Looper.getMainLooper());
    private SettingsStore store;
    private SearchConfig config;
    private SearchSession session;
    private LinearLayout root, page, resultContainer, statusContainer;
    private EditText queryField, keyField, endpointField, scopeField, templateField, domainField, countField;
    private Spinner categorySpinner, typeSpinner;
    private TextView scopeSummary, queryPreview, formError;
    private Button searchButton;
    private boolean dark, assembling;
    private int bg, surface, ink, muted, accent, soft, border, danger;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        bg = color(dark ? "#111821" : "#F8FAFC");
        surface = color(dark ? "#1B2633" : "#FFFFFF");
        ink = color(dark ? "#F0F4FA" : "#10243A");
        muted = color(dark ? "#B6C4D4" : "#516174");
        accent = color(dark ? "#A9C8FF" : "#1E3A5F");
        soft = color(dark ? "#263A51" : "#E8EEF5");
        border = color(dark ? "#52647A" : "#8192A6");
        danger = color(dark ? "#FFB4AB" : "#AF2525");
        store = new SettingsStore(this); config = store.load();
        session = (SearchSession) getLastNonConfigurationInstance();
        if (session == null) session = new SearchSession();
        if (state != null && session.query.isEmpty()) session.query = state.getString("query", "");
        if (session.settingsOpen) renderSettings(); else renderSearch();
        session.attach(() -> main.post(() -> {
            if (!isFinishing() && !isDestroyed() && !session.settingsOpen) renderResults();
        }));
    }
    @Override public Object onRetainNonConfigurationInstance() {
        if (session.settingsOpen && keyField != null) session.draft = readDraft();
        else if (queryField != null) session.query = queryField.getText().toString();
        return session;
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        if (queryField != null) session.query = queryField.getText().toString();
        state.putString("query", session.query);
        super.onSaveInstanceState(state);
    }
    @Override protected void onDestroy() {
        session.detach(); main.removeCallbacksAndMessages(null);
        if (!isChangingConfigurations()) session.close();
        super.onDestroy();
    }
    @Override public void onBackPressed() {
        if (session.settingsOpen) {
            SearchConfig draft = readDraft();
            if (!same(draft, config)) {
                new AlertDialog.Builder(this).setTitle("放弃未保存的设置？")
                        .setMessage("返回后将保留上次保存的配置。")
                        .setNegativeButton("继续编辑", null).setPositiveButton("放弃更改", (d,w) -> leaveSettings()).show();
            } else leaveSettings();
        } else super.onBackPressed();
    }
    private void leaveSettings() { session.settingsOpen = false; session.draft = null; hideKeyboard(); renderSearch(); }

    private void scaffold(String title, boolean settings) {
        assembling = true;
        FrameLayout outer = new FrameLayout(this); outer.setBackgroundColor(bg);
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        FrameLayout.LayoutParams centered = new FrameLayout.LayoutParams(
                getResources().getConfiguration().screenWidthDp > 720 ? dp(720) : -1, -1, Gravity.CENTER_HORIZONTAL);
        outer.addView(root, centered); setContentView(outer);
        root.setFitsSystemWindows(true);
        if (android.os.Build.VERSION.SDK_INT >= 35) {
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom); return insets;
            });
        }
        getWindow().setStatusBarColor(bg); getWindow().setNavigationBarColor(bg);
        if (android.os.Build.VERSION.SDK_INT >= 27) {
            getWindow().getDecorView().setSystemUiVisibility(dark ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
        LinearLayout toolbar = row(); toolbar.setPadding(dp(20),dp(8),dp(16),dp(8));
        if (settings) {
            Button back = button("返回", false); back.setContentDescription("返回搜索，未保存的设置会提示确认");
            toolbar.addView(back, new LinearLayout.LayoutParams(-2,-2)); back.setOnClickListener(v -> onBackPressed());
        } else {
            FrameLayout logo = new FrameLayout(this); logo.setBackground(shape(accent,12,0));
            logo.addView(new Glyph(this,Glyph.SEARCH,dark ? bg : Color.WHITE), new FrameLayout.LayoutParams(dp(28),dp(28),Gravity.CENTER));
            toolbar.addView(logo,new LinearLayout.LayoutParams(dp(40),dp(40)));
        }
        TextView brand = text(title, settings ? 22 : 23, ink, true);
        LinearLayout.LayoutParams brandLp = new LinearLayout.LayoutParams(0,-2,1); brandLp.leftMargin=dp(12);
        toolbar.addView(brand,brandLp);
        if (!settings) {
            Button gear = button("设置", false); gear.setContentDescription("打开 API 与默认搜索范围设置");
            toolbar.addView(gear,new LinearLayout.LayoutParams(-2,-2));
            gear.setOnClickListener(v -> { session.query=queryField.getText().toString(); session.settingsOpen=true; session.draft=null; renderSettings(); });
        }
        root.addView(toolbar,new LinearLayout.LayoutParams(-1,-2));
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setClipToPadding(false);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        page = column(); page.setPadding(dp(24),dp(16),dp(24),dp(32)); scroll.addView(page);
    }

    private void renderSearch() {
        scaffold("ExaScope",false);
        boolean initial = session.sentQuery.isEmpty();
        if (initial) {
            TextView eyebrow=text("你的范围，你的搜索",12,accent,true); eyebrow.setLetterSpacing(.12f); add(page,eyebrow,16);
            TextView headline=text("让好资料，\n更容易找到。",34,ink,true); headline.setLineSpacing(dp(2),1.05f); add(page,headline,16);
            add(page,text("为每一次搜索，带上你关心的范围。",15,muted,false),24);
        }
        LinearLayout scopeBox=column(); scopeBox.setPadding(dp(16),dp(12),dp(16),dp(12)); scopeBox.setBackground(shape(soft,16,0));
        add(scopeBox,text("默认搜索范围",12,muted,true),4);
        scopeSummary=text(config.scopeLabel(),15,accent,true); scopeSummary.setMaxLines(3); add(scopeBox,scopeSummary,0);
        add(page,scopeBox,16);
        TextView queryLabel=text("搜索内容",13,muted,true); add(page,queryLabel,8);
        queryField=field("搜索主题、问题或关键词",session.query,false); queryField.setId(QUERY_ID); queryLabel.setLabelFor(QUERY_ID);
        queryField.setSingleLine(true); queryField.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        queryField.setFilters(new InputFilter[]{new InputFilter.LengthFilter(2000)});
        queryField.setOnEditorActionListener((v,action,event)-> { if(action==EditorInfo.IME_ACTION_SEARCH){startSearch();return true;}return false; });
        add(page,queryField,12);
        searchButton=button(session.busy ? "取消搜索" : "搜索",true); add(page,searchButton,8);
        searchButton.setOnClickListener(v->{ if(session.busy)session.cancel(); else startSearch(); });
        Button preview=button("查看实际查询",false); add(page,preview,12);
        preview.setOnClickListener(v->{
            String q=queryField.getText().toString().trim();
            if(q.isEmpty())q="你的关键词";
            new AlertDialog.Builder(this).setTitle("将发送给 Exa 的查询")
                    .setMessage(config.effectiveQuery(q)).setPositiveButton("知道了",null).show();
        });
        if (initial) {
            add(page,text("试试这些主题",12,muted,true),8);
            HorizontalScrollView suggestions=new HorizontalScrollView(this); suggestions.setHorizontalScrollBarEnabled(false);
            LinearLayout chips=row();
            for(String sample:new String[]{"大语言模型的检索增强", "钠离子电池研究", "Android 官方开发指南"}) {
                Button chip=button(sample,false); chip.setBackground(ripple(shape(surface,24,border)));
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.rightMargin=dp(8); chips.addView(chip,lp);
                chip.setOnClickListener(v->{queryField.setText(sample);queryField.setSelection(sample.length());});
            }
            suggestions.addView(chips);add(page,suggestions,20);
        }
        statusContainer=column(); add(page,statusContainer,12);
        resultContainer=column();add(page,resultContainer,12);
        assembling=false; renderResults();
    }

    private void startSearch() {
        String query=queryField.getText().toString().trim();
        if(query.isEmpty()){queryField.setError("请输入搜索内容");queryField.requestFocus();return;}
        config=store.load();
        if(config.apiKey.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("先连接 Exa")
                    .setMessage("在设置中填写自己的 Exa API Key，即可开始搜索。")
                    .setNegativeButton("稍后",null).setPositiveButton("去设置",(d,w)->{
                        session.query=query;session.settingsOpen=true;renderSettings();
                    }).show();return;
        }
        try {config.validate();config.effectiveQuery(query);}catch(IllegalArgumentException ex){toast(ex.getMessage());return;}
        hideKeyboard();session.query=query;
        session.search(config,query);
        renderSearch();
    }

    private void renderResults() {
        if(statusContainer==null || session.settingsOpen)return;
        searchButton.setText(session.busy?"取消搜索":"搜索");
        statusContainer.removeAllViews(); resultContainer.removeAllViews();
        if(session.busy) {
            LinearLayout loading=row();ProgressBar progress=new ProgressBar(this);loading.addView(progress,new LinearLayout.LayoutParams(dp(28),dp(28)));
            TextView status=text("正在查找相关来源…",14,muted,false);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.leftMargin=dp(12);loading.addView(status,lp);
            add(statusContainer,loading,8);return;
        }
        if(!session.error.isEmpty()) {
            TextView error=text(session.error,15,danger,false);error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);add(statusContainer,error,8);
            return;
        }
        if(session.response==null) {
            if(config.apiKey.isEmpty()) {
                add(statusContainer,text("首次使用：在右上角设置中填写 API Key。",14,muted,false),0);
            }
            if(!store.keyError.isEmpty())add(statusContainer,text(store.keyError,14,danger,false),8);
            return;
        }
        ExaClient.Response response=session.response;
        String meta=String.format(Locale.CHINA,"%d 条来源  ·  %.1f 秒",response.results.size(),session.elapsedMs/1000.0);
        if(response.cost>=0)meta+=String.format(Locale.US,"  ·  $%.4f",response.cost);
        TextView summary=text(meta,13,muted,false);summary.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);add(statusContainer,summary,8);
        Button actual=button("本次实际查询",false);add(statusContainer,actual,4);
        actual.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("本次实际查询").setMessage(session.sentQuery).setPositiveButton("关闭",null).show());
        if(response.results.isEmpty()) {
            add(resultContainer,text("暂时没有找到相关结果",22,ink,true),8);
            add(resultContainer,text("换个关键词，或到设置中放宽分类、域名与默认范围。",15,muted,false),16);return;
        }
        int index=0;
        for(ExaClient.Result result:response.results) {
            LinearLayout card=column();card.setPadding(dp(18),dp(18),dp(18),dp(12));card.setBackground(shape(surface,18,0));
            String host=URI.create(result.url).getHost();
            add(card,text(String.format(Locale.US,"%02d  /  %s",++index,host),12,muted,true),10);
            TextView title=text(result.title,19,ink,true);title.setTextIsSelectable(true);add(card,title,8);
            List<String> details=new ArrayList<>();if(!result.author.isEmpty())details.add(result.author);
            if(!result.date.isEmpty())details.add(result.date.length()>10?result.date.substring(0,10):result.date);
            if(!details.isEmpty())add(card,text(String.join("  ·  ",details),12,muted,false),10);
            if(!result.snippet.isEmpty()) {
                TextView snippet=text(result.snippet,15,muted,false);snippet.setMaxLines(6);snippet.setTextIsSelectable(true);add(card,snippet,8);
                if(result.snippet.length()>180) {
                    Button expand=button("展开摘要",false);add(card,expand,0);
                    expand.setOnClickListener(v->{boolean open=snippet.getMaxLines()==6;snippet.setMaxLines(open?Integer.MAX_VALUE:6);expand.setText(open?"收起摘要":"展开摘要");});
                }
            }
            LinearLayout actions=row();Button open=button("打开来源",false);Button copy=button("复制链接",false);
            actions.addView(open,new LinearLayout.LayoutParams(0,-2,1));actions.addView(copy,new LinearLayout.LayoutParams(0,-2,1));add(card,actions,0);
            open.setOnClickListener(v->openUrl(result.url));copy.setOnClickListener(v->copy(result.url));
            add(resultContainer,card,16);
        }
        add(resultContainer,text("来源由 Exa 返回，摘要为网页摘录。",12,muted,false),0);
    }

    private void renderSettings() {
        SearchConfig current=session.draft==null?config:session.draft;
        scaffold("搜索设置",true);
        add(page,text("连接服务，定义你的搜索范围。",15,muted,false),24);
        LinearLayout connection=section("01", "API 连接");
        endpointField=labeledField(connection,"API 地址","https://api.exa.ai",current.endpoint,false,0);
        add(connection,text("支持 Exa 兼容服务；填写基础地址或完整 /search 地址。",12,muted,false),16);
        keyField=labeledField(connection,"API Key","填写你的 Exa API Key",current.apiKey,false,KEY_ID);
        keyField.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        keyField.setTransformationMethod(PasswordTransformationMethod.getInstance());
        keyField.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        CheckBox show=new CheckBox(this);show.setText("显示 Key");show.setTextColor(muted);show.setTextSize(14);show.setMinHeight(dp(48));add(connection,show,4);
        show.setOnCheckedChangeListener((b,checked)->{keyField.setTransformationMethod(checked?null:PasswordTransformationMethod.getInstance());keyField.setSelection(keyField.length());});
        add(connection,text("Key 使用 Android Keystore 加密，仅随搜索请求发送至你设置的 API 地址。",12,muted,false),8);
        Button dashboard=button("获取 Exa API Key",false);add(connection,dashboard,0);dashboard.setOnClickListener(v->openUrl("https://dashboard.exa.ai/api-keys"));
        if(!store.keyError.isEmpty())add(connection,text(store.keyError,14,danger,false),8);
        add(page,connection,24);

        LinearLayout range=section("02","默认搜索范围");
        add(range,text("范围预设",13,muted,true),8);
        Spinner preset=spinner(SearchConfig.PRESET_NAMES,4);add(range,preset,12);
        scopeField=labeledField(range,"范围描述（自动注入）","例如：机器学习领域的学术论文，优先近年的原始研究",current.scope,true,SCOPE_ID);
        scopeField.setFilters(new InputFilter[]{new InputFilter.LengthFilter(2000)});
        add(range,text("每次搜索自动带上这段描述；留空则仅搜索你输入的关键词。",12,muted,false),16);
        add(range,text("Exa 分类筛选",13,muted,true),8);
        categorySpinner=spinner(SearchConfig.CATEGORY_LABELS,Math.max(0,Arrays.asList(SearchConfig.CATEGORIES).indexOf(current.category)));add(range,categorySpinner,16);
        domainField=labeledField(range,"限定来源域名（可选）","arxiv.org, pubmed.ncbi.nlm.nih.gov",current.domains,true,0);
        add(range,text("多个域名用逗号或空格分隔；留空则不限制。域名和分类是硬筛选，范围描述影响相关性。",12,muted,false),8);
        preset.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            boolean first=true;
            @Override public void onItemSelected(AdapterView<?> p,View v,int position,long id){
                if(first){first=false;return;}
                if(position<4){scopeField.setText(SearchConfig.PRESET_SCOPES[position]);categorySpinner.setSelection(Arrays.asList(SearchConfig.CATEGORIES).indexOf(SearchConfig.PRESET_CATEGORIES[position]));}
            }
            @Override public void onNothingSelected(AdapterView<?> p){}
        });
        add(page,range,24);

        LinearLayout request=section("03","查询与结果");
        templateField=labeledField(request,"查询模板","使用 {query} 和 {scope}",current.template,true,TEMPLATE_ID);
        templateField.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4000)});
        add(request,text("{query} 替换为搜索内容，{scope} 替换为范围。缺少占位符时仍会自动补入，避免漏掉关键词或范围。",12,muted,false),12);
        queryPreview=text("",13,accent,false);queryPreview.setPadding(dp(12),dp(12),dp(12),dp(12));queryPreview.setBackground(shape(soft,12,0));add(request,queryPreview,16);
        TextWatcher watcher=new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){updatePreview();}public void afterTextChanged(Editable e){}};
        scopeField.addTextChangedListener(watcher);templateField.addTextChangedListener(watcher);
        add(request,text("搜索模式",13,muted,true),8);
        typeSpinner=spinner(new String[]{"Auto · 质量与速度均衡","Lite · 更快、更低成本"},current.searchType.equals("lite")?1:0);add(request,typeSpinner,16);
        countField=labeledField(request,"每次结果数量","1–100",Integer.toString(current.resultCount),false,0);countField.setInputType(InputType.TYPE_CLASS_NUMBER);countField.setFilters(new InputFilter[]{new InputFilter.LengthFilter(3)});
        add(request,text("结果越多可能产生更多 API 费用；默认 10 条。",12,muted,false),8);add(page,request,16);
        formError=text("",14,danger,false);formError.setVisibility(View.GONE);formError.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);add(page,formError,12);
        Button save=button("保存设置",true);add(page,save,16);
        save.setOnClickListener(v->saveSettings());
        add(page,text("ExaScope 1.0.0\n原生纯搜索 · 无广告 · 无分析追踪\n搜索请求直接发送至你配置的服务，打开来源时由浏览器访问。",12,muted,false),12);
        assembling=false; updatePreview();
    }

    private SearchConfig readDraft() {
        int count;try{count=Integer.parseInt(countField.getText().toString());}catch(NumberFormatException e){count=0;}
        return new SearchConfig(endpointField.getText().toString(),keyField.getText().toString(),scopeField.getText().toString(),templateField.getText().toString(),
                SearchConfig.CATEGORIES[categorySpinner.getSelectedItemPosition()],domainField.getText().toString(),typeSpinner.getSelectedItemPosition()==1?"lite":"auto",count);
    }
    private void saveSettings() {
        try {
            SearchConfig next=readDraft();next.validate();
            store.save(next);config=next;session.draft=null;session.settingsOpen=false;hideKeyboard();renderSearch();toast("设置已保存");
        } catch(IllegalArgumentException ex){showFormError(ex.getMessage());}
        catch(Exception ex){showFormError("无法安全保存设置，请检查设备存储后重试");}
    }
    private void showFormError(String message){formError.setText(message);formError.setVisibility(View.VISIBLE);formError.requestFocus();}
    private void updatePreview(){if(assembling||queryPreview==null)return;SearchConfig draft=readDraft();queryPreview.setText("查询预览\n"+draft.effectiveQuery("检索增强生成"));}
    private boolean same(SearchConfig a,SearchConfig b){return a.endpoint.equals(b.endpoint)&&a.apiKey.equals(b.apiKey)&&a.scope.equals(b.scope)&&a.template.equals(b.template)&&a.category.equals(b.category)&&a.domains.equals(b.domains)&&a.searchType.equals(b.searchType)&&a.resultCount==b.resultCount;}

    private LinearLayout section(String number,String title){
        LinearLayout box=column();box.setPadding(dp(18),dp(20),dp(18),dp(16));box.setBackground(shape(surface,18,0));
        add(box,text(number+"   "+title,19,ink,true),20);return box;
    }
    private EditText labeledField(LinearLayout target,String label,String hint,String value,boolean multiline,int id){
        TextView caption=text(label,13,muted,true);add(target,caption,8);
        EditText edit=field(hint,value,multiline);if(id==0)id=View.generateViewId();edit.setId(id);caption.setLabelFor(id);add(target,edit,12);return edit;
    }
    private EditText field(String hint,String value,boolean multiline){
        EditText edit=new EditText(this);edit.setTextSize(16);edit.setTextColor(ink);edit.setHintTextColor(muted);edit.setHint(hint);edit.setText(value);
        edit.setBackground(shape(bg,12,border));edit.setPadding(dp(14),dp(12),dp(14),dp(12));edit.setMinHeight(dp(56));
        edit.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES|(multiline?InputType.TYPE_TEXT_FLAG_MULTI_LINE:0));
        if(multiline){edit.setMinLines(2);edit.setGravity(Gravity.TOP|Gravity.START);}else edit.setSingleLine(true);
        edit.setSelectAllOnFocus(false);return edit;
    }
    private Spinner spinner(String[] items,int selected){
        Spinner spinner=new Spinner(this);ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,items);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);spinner.setAdapter(adapter);spinner.setSelection(selected);
        spinner.setMinimumHeight(dp(56));spinner.setPadding(dp(8),0,dp(8),0);spinner.setBackground(shape(bg,12,border));return spinner;
    }
    private Button button(String label,boolean primary){
        Button button=new Button(this);button.setText(label);button.setTextSize(15);button.setAllCaps(false);button.setMinHeight(dp(primary?56:48));
        button.setMinimumWidth(dp(48));button.setPadding(dp(16),dp(8),dp(16),dp(8));button.setTextColor(primary?(dark?bg:Color.WHITE):accent);
        button.setTypeface(Typeface.DEFAULT,Typeface.BOLD);button.setStateListAnimator(null);
        button.setBackground(ripple(shape(primary?accent:Color.TRANSPARENT,primary?14:12,0)));return button;
    }
    private TextView text(String value,int size,int tint,boolean bold){
        TextView view=new TextView(this);view.setText(value);view.setTextSize(size);view.setTextColor(tint);
        view.setLineSpacing(dp(3),1.05f);view.setIncludeFontPadding(false);if(bold)view.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return view;
    }
    private void add(LinearLayout parent,View view,int bottom){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(bottom);parent.addView(view,lp);}
    private LinearLayout column(){LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);return layout;}
    private LinearLayout row(){LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.HORIZONTAL);layout.setGravity(Gravity.CENTER_VERTICAL);return layout;}
    private GradientDrawable shape(int fill,int radius,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(radius));if(stroke!=0)d.setStroke(dp(1),stroke);return d;}
    private RippleDrawable ripple(GradientDrawable background){return new RippleDrawable(ColorStateList.valueOf(dark?0x33FFFFFF:0x22000000),background,null);}
    private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private int color(String hex){return Color.parseColor(hex);}
    private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_SHORT).show();}
    private void copy(String url){((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("来源链接",url));toast("链接已复制");}
    private void openUrl(String url){
        if(!ExaClient.isWebUrl(url)){toast("链接格式无效");return;}
        try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE));}
        catch(ActivityNotFoundException ex){toast("未找到可打开链接的浏览器");}
    }
    private void hideKeyboard(){View focus=getCurrentFocus();if(focus!=null)((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(focus.getWindowToken(),0);}
}
