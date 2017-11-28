package site.site8.updateapk.updateapk;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;

import site.site8.updateapk.updateapp.UpdateService;

public class MainActivity extends AppCompatActivity {
    private String url="http://ucdl.25pp.com/fs01/union_pack/Wandoujia_209279_web_seo_baidu_homepage.apk";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    public void update(View view) {
        UpdateService.Builder.create(url)
                .setVersionCode(2)
                .setIsForce(true)
                .build(MainActivity.this);
    }
}
