package util.logging

import java.util
import java.util.concurrent.ConcurrentHashMap

import org.slf4j.spi.MDCAdapter
import zio.FiberRef

class FiberMDCAdapter extends MDCAdapter {

  private val fiberRef       = zio.Runtime.global.unsafeRun(FiberRef.make[util.Map[String, String]](null))
  private val threadLocalMap = zio.Runtime.global.unsafeRun(fiberRef.unsafeAsThreadLocal)

  override def put(key: String, value: String): Unit = {
    val map = threadLocalMap.get()
    if (map == null) {
      val newMap = new ConcurrentHashMap[String, String]()
      newMap.put(key, value)
      threadLocalMap.set(newMap)
    } else {
      map.put(key, value)
      ()
    }
  }

  override def get(key: String): String = {
    val map = threadLocalMap.get()
    if (map != null) map.get(key) else null
  }

  override def remove(key: String): Unit = {
    val map = threadLocalMap.get()
    if (map != null) {
      map.remove(key)
      ()
    }

  }

  override def clear(): Unit =
    threadLocalMap.remove()

  override def getCopyOfContextMap: util.Map[String, String] = {
    val map = threadLocalMap.get()
    if (map == null) null else new util.HashMap(map)
  }

  override def setContextMap(contextMap: util.Map[String, String]): Unit =
    threadLocalMap.set(new ConcurrentHashMap(contextMap))
}
