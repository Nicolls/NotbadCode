package com.notbad.hotfix.plugin

import android.content.Intent
import android.content.res.Resources
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.notbad.lib.common.LogUtils
import java.lang.Exception

private const val TAG = "PluginMainActivity"

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LogUtils.d(TAG, "onCreate ")
        setContentView(R.layout.activity_main)
    }

    override fun getResources(): Resources {
        if(application!=null) {
            LogUtils.d(TAG, "start to get getResources")
            try {
                val resMethod = application::class.java.getDeclaredMethod("getPluginResources", String::class.java)
                resMethod.isAccessible = true
                val res= resMethod.invoke(application,"com.notbad.mmmmmm") as Resources
                return res
            }catch (e: Exception) {
                LogUtils.e(TAG, "getResule error", e)
            }
        }
        return super.getResources()
    }

    fun onTest(view: View) {
        LogUtils.d(TAG, "onTest ")
        startActivity(Intent(this, SecondActivity::class.java))
    }

    fun onTest2(view: View) {
        LogUtils.d(TAG, "onTest2 ")
        startActivity(Intent(this, MyPluginActivity::class.java))
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        LogUtils.d(TAG, "onNewIntent ${hashCode()}")
    }

    override fun onDestroy() {
        super.onDestroy()
        LogUtils.d(TAG, "onDestroy ${hashCode()}")
    }
}