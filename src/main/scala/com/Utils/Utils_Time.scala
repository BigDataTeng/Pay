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
            Date:2019/08/29 15:44
                    @author Mr.Liu 
                    @version : 1.0
*/
import java.text.SimpleDateFormat

/**
  * 工具类
  */
object Utils_Time {

  //时间工具类
  def consttiem(startTime:String,stopTime:String):Long={
    val df = new SimpleDateFormat("yyyyMMddHHmmssSSS")
    // 20170412030013393282687799171031
    // 开始时间
    val st: Long = df.parse(startTime.substring(0,17)).getTime
    // 结束时间
    val et = df.parse(stopTime).getTime
    et-st
  }

}
