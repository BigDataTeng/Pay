package com.Utils

/*
                    .::::.
                  .::::::::.
                 :::::::::::
             ..:::::::::::'	  FUCK YOU
           '::::::::::::'		Goddess bless, never BUG
             .::::::::::
        '::::::::::::::..
             ..::::::::::::.
           ``::::::::::::::::
            ::::``:::::::::'        .:::.
           ::::'   ':::::'       .::::::::.
         .::::'      ::::     .:::::::'::::.
        .:::'       :::::  .:::::::::' ':::::.
       .::'        :::::.:::::::::'      ':::::.
      .::'         ::::::::::::::'         ``::::.
  ...:::           ::::::::::::'              ``::.
 ```` ':.          ':::::::::'                  ::::..
                    '.:::::'                    ':'````..

 ━━━━━━━━━━━━━━━━━━━━ 女神保佑,永无BUG ━━━━━━━━━━━━━━━━━━━━
                     Description：xxxx
                  Copyright(c),2019,Beijing
          This program is protected by copyright laws.
            Date:2019/08/29 15:46
                    @author Mr.Liu 
                    @version : 1.0
*/
import java.sql.{Connection, DriverManager}
import java.util

/**
  * mysql连接池
  */
object ConnectPoolUtils {
  private val max = 50 //连接池总数
  private val connectionNum = 10 // 每次产生的连接数
  private val pool = new util.LinkedList[Connection]() // 连接池
  private var conNum = 0 // 当前产生的连接数

  // 获取连接
  def getConnections():Connection={
    // 同步代码块
    AnyRef.synchronized({
      if(pool.isEmpty){
        // 加载驱动
        preGetConn()
        for(i<- 1 to connectionNum){
          val conn = DriverManager.getConnection("jdbc:mysql://hadoop01:3306/pay?useUnicode=true&characterEncoding=utf8","root","123456")
          pool.push(conn)
          conNum += 1
        }
      }
      pool.poll()
    })
  }
  // 释放连接
  def resultConn(conn:Connection): Unit ={
    pool.push(conn)
  }

  // 加载驱动
  def preGetConn(): Unit ={
    // 控制驱动
    if(conNum > max){
      println("无连接")
      Thread.sleep(2000)
      preGetConn()
    }else{
      Class.forName("com.mysql.jdbc.Driver")
    }
  }
}
