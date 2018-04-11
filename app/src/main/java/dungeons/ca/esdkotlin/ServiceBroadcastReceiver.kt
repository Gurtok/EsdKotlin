package dungeons.ca.esdkotlin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class ServiceBroadcastReceiver: BroadcastReceiver(){

  override fun onReceive(context: Context?, intent: Intent?) {
    Log.e("ServiceReceiver", "Starting service manager as its own service!!")
    context?.startService( Intent(context, ServiceManager::class.java))
  }

}