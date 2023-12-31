package com.notbad.hotfix

import android.content.Context
import android.content.Intent
import android.content.res.AssetManager
import android.content.res.Resources
import com.notbad.lib.common.LogUtils
import dalvik.system.DexClassLoader
import dalvik.system.PathClassLoader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.lang.reflect.Array

/**
 * 热修复，加载外部Dex
 */
object HotFixManager {
    private const val TAG = "HotFixManager"

    fun loadDex(context: Context) {

        val dexPath = exToCache(context)
        dexPath?.let {
            handleDex(context,dexPath)
        }
    }

    private fun exToCache(context: Context):String?{
        val dexFileName = "update.apk"

        // 加载外部存储器上的
        val dexPath = "${context.getExternalFilesDir(null)?.absolutePath}/$dexFileName"
        LogUtils.d(TAG, "dexPath:$dexPath")
        val dexFile = File(dexPath)
        if (!dexFile.exists()) {
            LogUtils.w(TAG, "dex file not found $dexPath")
            return null
        }
        LogUtils.d(TAG, "load dex file from storage")
        val ips = FileInputStream(dexFile)
        // 把这个dex弄到我们的cache code目录下
        val cacheDexFile = File(context.cacheDir, dexFileName)
        if (cacheDexFile.exists()) {
            val d = cacheDexFile.delete()
            LogUtils.i(TAG, "delete cache dex $d")
        }
        LogUtils.i(TAG, "copy dex to cache ${cacheDexFile.path}")
        val ops = FileOutputStream(cacheDexFile)
        try {
            var len: Int = 0
            val buffer = ByteArray(1024)
            while (ips.read(buffer).also { len = it } != -1) {
                ops.write(buffer, 0, len)
            }
            ops.flush()
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        } finally {
            try {

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return if (!cacheDexFile.exists()) {
            LogUtils.i(TAG, "copy fail!")
            null
        } else {
            LogUtils.i(TAG, "copy success!")
            cacheDexFile.absolutePath
        }
    }

    private fun handleDex(context: Context, dexPath: String) {
        try {
            // 首先获取PathClassLoader加载的系统类等
            val pathClassLoader = context.classLoader as PathClassLoader
            val baseDexClass = pathClassLoader.javaClass.superclass
            val pathListField = baseDexClass.getDeclaredField("pathList")
            pathListField.isAccessible = true
            val pathListObject = pathListField.get(pathClassLoader)

            val systemDexPathListClass = pathListObject.javaClass
            val systemElementsField = systemDexPathListClass.getDeclaredField("dexElements")
            systemElementsField.isAccessible = true
            val systemElements = systemElementsField[pathListObject]

            // 自定义class loader

            // 自定义DexClassLoader定义要载入的补丁dex，此处其实可以将多个dex用「:」隔开，则无需遍历
            val dexClassLoader =
                DexClassLoader(dexPath, null, null, context.classLoader)

            val customDexClassLoader = Class.forName("dalvik.system.BaseDexClassLoader")
            val customPathListFiled = customDexClassLoader.getDeclaredField("pathList")
            customPathListFiled.isAccessible = true
            val customDexPathListObject = customPathListFiled[dexClassLoader]

            val customPathClass: Class<*> = customDexPathListObject.javaClass
            val customElementsField = customPathClass.getDeclaredField("dexElements")
            customElementsField.isAccessible = true
            val customElements = customElementsField[customDexPathListObject]

            // 合并数组
            val elementClass = systemElements.javaClass.componentType
            val systemLength = Array.getLength(systemElements)
            val customLength = Array.getLength(customElements)
            val newSystemLength = systemLength + customLength

            // 生成一个新的数组，类型为Element类型
            val newElementsArray = Array.newInstance(elementClass, newSystemLength)
            for (i in 0 until newSystemLength) {
                if (i < customLength) {
                    Array.set(newElementsArray, i, Array.get(customElements, i))
                } else {
                    Array.set(newElementsArray, i, Array.get(systemElements, i - customLength))
                }
            }

            // 覆盖新数组
            val elementsField = pathListObject.javaClass.getDeclaredField("dexElements")
            elementsField.isAccessible = true
            elementsField[pathListObject] = newElementsArray


        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
        hookAmsH(context)
//        hotFixResources(context,dexPath)
    }



    fun hookAmsH(context: Context){
        LogUtils.d(TAG, "hookAmsH")
        HookManager.getInstance().hookAms(context)
        HookManager.getInstance().hookHandler(context)
    }


    /**
     * 重启应用
     */
    fun reCreate(context: Context){
        LogUtils.d(TAG, "reCreate")
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        context.startActivity(intent);
        //杀掉以前进程
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    /**
     * 资源热修复，因为热修复是对同一个apk来讲的，所以，我们只需要将新apk的资源替代的掉旧的apk资源即可
     */
    fun hotFixResources(context: Context,dexPath: String){
        try {
            val newAssertManager = AssetManager::class.java.getConstructor().newInstance()
            val addAssetPathMethod = newAssertManager::class.java.getDeclaredMethod("addAssetPath", String::class.java)
            addAssetPathMethod.isAccessible = true
            val result=addAssetPathMethod.invoke(newAssertManager,dexPath)
            LogUtils.d(TAG, "result $result")
            val res = context.resources
            val mResourcesImplFiled = Resources::class.java.getDeclaredField("mResourcesImpl")
            mResourcesImplFiled.isAccessible = true
            val resourceImpl = mResourcesImplFiled.get(res)
            val assetField = resourceImpl::class.java.getDeclaredField("mAssets")
            assetField.isAccessible = true
            assetField.set(resourceImpl,newAssertManager)
        }catch (e:Exception) {
            LogUtils.e(TAG, "hotFixResource error", e)
        }
    }

    /**
     * 资源hook插件化，插件化是增加资源，因为我们不能作替换，而是增加另一个apk的资源
     */
    fun hookResources(context: Context):Resources?{
        LogUtils.d(TAG, "hookResources ${context.applicationContext}")
        val dexPath = exToCache(context)
        dexPath?.run {
            try {
                val newAssertManager = AssetManager::class.java.getConstructor().newInstance()
                val addAssetPathMethod = newAssertManager::class.java.getDeclaredMethod("addAssetPath", String::class.java)
                addAssetPathMethod.isAccessible = true
                val result=addAssetPathMethod.invoke(newAssertManager,dexPath)
                LogUtils.d(TAG, "result $result")
                val res = Resources(newAssertManager,context.resources.displayMetrics,context.resources.configuration)
                return res
            }catch (e:Exception) {
                LogUtils.e(TAG, "hotFixResource error", e)
            }
        }
        return null
    }


}