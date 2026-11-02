package io.github.exascope;

import android.content.Context;
import android.content.Intent;
import android.app.Instrumentation;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Button;
import android.view.View;
import android.view.ViewGroup;
import android.graphics.Bitmap;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class DeviceSmokeTest {
    private Instrumentation getInstrumentation(){return InstrumentationRegistry.getInstrumentation();}
    @Test public void testKeystoreSettingsRoundTripAndCiphertext() throws Exception {
        Context context=getInstrumentation().getTargetContext();
        SettingsStore store=new SettingsStore(context);SearchConfig original=store.load();
        SearchConfig test=new SearchConfig("https://api.exa.ai","device-test-key","学术论文",SearchConfig.DEFAULT_TEMPLATE,"publication","arxiv.org","auto",10);
        try {
            store.save(test);SearchConfig restored=new SettingsStore(context).load();
            assertEquals("device-test-key",restored.apiKey);assertEquals("学术论文",restored.scope);
            String stored=context.getSharedPreferences("exascope_settings",Context.MODE_PRIVATE).getString("api_key","");
            assertFalse(stored.contains("device-test-key"));assertTrue(stored.contains(":"));
        } finally {store.save(original);}
    }
    @Test public void testActivityStartsAndHasEditableSearch() throws Exception {
        Intent intent=new Intent(getInstrumentation().getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        MainActivity activity=(MainActivity)getInstrumentation().startActivitySync(intent);
        try {getInstrumentation().runOnMainSync(()->{EditText input=activity.findViewById(1001);assertNotNull(input);input.setText("graph neural networks");assertEquals("graph neural networks",input.getText().toString());});}
        finally {getInstrumentation().runOnMainSync(activity::finish);}
    }
    @Test public void testSettingsSaveAndRangeAppearsOnSearch() throws Exception {
        SettingsStore store=new SettingsStore(getInstrumentation().getTargetContext());SearchConfig original=store.load();
        Intent intent=new Intent(getInstrumentation().getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        MainActivity activity=(MainActivity)getInstrumentation().startActivitySync(intent);
        try {
            getInstrumentation().runOnMainSync(()->{
                View settings=find(activity.getWindow().getDecorView(),"设置");assertNotNull(settings);settings.performClick();
                ((EditText)activity.findViewById(1002)).setText("device-test-key");
                ((EditText)activity.findViewById(1003)).setText("机器学习论文");
                ((EditText)activity.findViewById(1004)).setText("{query}\n范围：{scope}");
                View save=find(activity.getWindow().getDecorView(),"保存设置");assertNotNull(save);save.performClick();
                assertNotNull(activity.findViewById(1001));assertNotNull(find(activity.getWindow().getDecorView(),"机器学习论文"));
            });
            SearchConfig saved=store.load();assertEquals("机器学习论文",saved.scope);assertTrue(saved.effectiveQuery("RAG").contains("范围：机器学习论文"));
        } finally {getInstrumentation().runOnMainSync(activity::finish);store.save(original);}
    }
    @Test public void captureAppScreenshots() throws Exception {
        SettingsStore store=new SettingsStore(getInstrumentation().getTargetContext());SearchConfig original=store.load();
        store.save(new SearchConfig("https://api.exa.ai","","学术文献与论文，优先原始研究",SearchConfig.DEFAULT_TEMPLATE,"publication","","auto",10));
        Intent intent=new Intent(getInstrumentation().getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        MainActivity activity=(MainActivity)getInstrumentation().startActivitySync(intent);
        try {
            getInstrumentation().waitForIdleSync();saveScreenshot("search.png");
            getInstrumentation().runOnMainSync(()->find(activity.getWindow().getDecorView(),"设置").performClick());
            getInstrumentation().waitForIdleSync();saveScreenshot("settings.png");
        } finally {getInstrumentation().runOnMainSync(activity::finish);store.save(original);}
    }
    private void saveScreenshot(String name) throws Exception {
        Bitmap bitmap=getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull(bitmap);
        File folder=new File(getInstrumentation().getTargetContext().getExternalFilesDir(null),"screenshots");
        if(!folder.exists())assertTrue(folder.mkdirs());
        try(FileOutputStream output=new FileOutputStream(new File(folder,name))){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,output));}
        bitmap.recycle();
    }
    private View find(View view,String text) {
        if(view instanceof TextView && ((TextView)view).getText().toString().equals(text))return view;
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View result=find(group.getChildAt(i),text);if(result!=null)return result;}}
        return null;
    }
}
