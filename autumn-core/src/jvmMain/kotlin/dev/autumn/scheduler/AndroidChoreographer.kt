package dev.autumn.scheduler

import java.lang.reflect.Proxy

object AndroidChoreographer {
    
    val isAndroid: Boolean by lazy {
        System.getProperty("java.vendor")?.contains("Android", ignoreCase = true) == true
    }

    private val choreographerClass by lazy { Class.forName("android.view.Choreographer") }
    private val frameCallbackClass by lazy { Class.forName("android.view.Choreographer\$FrameCallback") }
    
    private val getInstanceMethod by lazy { choreographerClass.getMethod("getInstance") }
    private val postFrameCallbackMethod by lazy { choreographerClass.getMethod("postFrameCallback", frameCallbackClass) }
    private val removeFrameCallbackMethod by lazy { choreographerClass.getMethod("removeFrameCallback", frameCallbackClass) }

    fun start(scheduler: AutumnScheduler): Any {
        val choreographer = getInstanceMethod.invoke(null)
        var callbackArray = arrayOfNulls<Any>(1)
        
        val callback = Proxy.newProxyInstance(
            frameCallbackClass.classLoader ?: ClassLoader.getSystemClassLoader(),
            arrayOf(frameCallbackClass)
        ) { _, method, _ ->
            if (method.name == "doFrame") {
                scheduler.tick()
                postFrameCallbackMethod.invoke(choreographer, callbackArray[0])
            }
            null
        }
        
        callbackArray[0] = callback
        postFrameCallbackMethod.invoke(choreographer, callback)
        return callback
    }
    
    fun stop(callback: Any) {
        val choreographer = getInstanceMethod.invoke(null)
        removeFrameCallbackMethod.invoke(choreographer, callback)
    }
}