package com.example.demo.service

import com.example.demo.model.BranchItem
import com.example.demo.model.Division
import com.example.demo.model.GetBranchReq
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.math.RoundingMode
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.math.*


@Service
class GetBranchService(val db: JdbcTemplate) {

    val earthRadius = 6371.032;
    val PI = 3.141592


    fun getBranch(req: GetBranchReq): List<BranchItem> {

         var branch: List<BranchItem>
            if(
                req.longitude != null
                &&
                req.latitude != null
                &&
                req.all == 0
                &&
                req.division == null
                )
            {
                 branch =  getSpoList(req.longitude, req.latitude)
            } else if(
                req.longitude != null
                &&
                req.latitude != null
                &&
                req.all == 1
                &&
                req.division == null
            )
            {
                val divisiionNum = getDivision(req.latitude, req.longitude, 1, 20.0)
                branch = getSpoList(divisiionNum)


            } else if (
                req.longitude == null
                &&
                req.latitude == null
                &&
                req.division != null
            )
            {
                    branch =  getSpoList(req.division)

            }

        return branch
    }

    fun getDivision(
        pLatitude: Double,
        pLongitude: Double,
        pMainDiv: Int?,
        pDistance: Double
    ): Int {

        val sql =
            """ 
                SELECT t.pdivision, t.latitude, t.longitude, ROUND(t.distance, 2)
                 FROM (SELECT pdivision, gps.latitude, gps.longitude, gps.distance
                       FROM division division,
                            (SELECT sdivision, latitude, longitude, (6371.032
                            * ACOS (SIN ($pLatitude * 3.141592 / 180) * SIN (po.latitude * 3.141592 / 180)
                            + COS ($pLatitude * 3.141592 / 180) * COS (po.latitude * 3.141592 / 180)
                            * COS ($pLongitude * 3.141592 / 180 - po.longitude * 3.141592 / 180))) distance
                            FROM division po
                            ORDER BY distance) gps
                       WHERE division.pdivision = gps.sdivision
                       AND status = 1
                       AND division.sdivision = DECODE($pMainDiv, null, division.sdivision, 1388)
                       ORDER BY gps.distance) t
                 WHERE ROWNUM = 1
                 """



        val division: List<Division> = db.query(sql) { rs, _ ->

            Division(
                rs.getInt("pdivision"),
                rs.getDouble("latitude"),
                rs.getDouble("longitude"),
                rs.getDouble("distance")
            )


        }


        return division[0].pdivision
    }



    fun getSpoList(p_division: Int): List<BranchItem>
    {
        val query  =
            """
            SELECT division.pdivision,
                       description,
                       address,
                       NVL(latitude,(SELECT latitude FROM division WHERE pdivision = $p_division)) latitude,
                       NVL(longitude,(SELECT longitude FROM division WHERE pdivision = $p_division)) longitude,
                       division as code
                    FROM (SELECT pdivision,
                                 description,
                                 address,
                                 division
                            FROM division division
                           WHERE sdivision = $p_division
                             AND status = 1
                             AND division.division not in (SELECT REGEXP_SUBSTR(str, '[^,]+', 1, LEVEL) prod_code
                  FROM (SELECT pkg_mc_config.fget('REPAIRING_DIVISION', 'MB') str FROM dual)               
               CONNECT BY INSTR(str, ',', 1, LEVEL-1) > 0)) division,
                        (SELECT pdivision, latitude, longitude
                           FROM division
                        ) gps
                    WHERE division.pdivision = gps.pdivision(+)
                 ORDER BY division.description
        """.trimIndent()

        val result: List<BranchItem> = db.query(query) { response, _ ->
            BranchItem(
                response.getInt("pdevice"),
                response.getString("description"),
                response.getString("address"),
                response.getDouble("latitude"),
                response.getDouble("longitude"),
                response.getString("type"),
                response.getString("code"),
                response.getString("nearest_distance")
            )
        }

        return result

    }

/*
* 1 ПОДНЯТЬ БАЗУ ДАННЫХ
* 2 ВЫНЕСТИ ИЗ МЕТОДОВ ЛОГИКУ ВЫЧИСЛЕНИЯ ДИСТАНЦИИ И ВСТАВИТЬ В САМ ЗАПРОС
* 3 НАЙТИ СПОСОБ СОРТИРОВКИ РЕЗУЛЬТАТОВ ЗАПРОСА
* 4 ВЫНЕСТИ CASE В КОД
*
*
*
*
* */

    fun getSpoList(lon: Double, lat: Double): List<BranchItem> {




        val query =
            """
            SELECT division.pdivision,
               description,
               NULL address, -- їїїїїїїїїї
               division.latitude,
               division.longitude,
               division.division as code
          FROM division division,
               (SELECT pdivision,
                       latitude,
                       longitude,
                         /***********  УБРАТЬ ЭТУ КОЛОНКУ В МЕТОД DISTANCE  И НАЙТИ СПОСОБ СОРТИРОВКИ ПО КОЛОНКЕ DISTANCE ********/            
                      ) gps
         WHERE status = 1
           AND division.pdivision = gps.pdivision
           AND ROWNUM < 11;
        """.trimIndent()

        var branchList: List<BranchItem> = db.query(query) { response, _ ->
             BranchItem(
                 response.getInt("pdevice"),
                 response.getString("description"),
                 response.getString("address"),
                 response.getDouble("latitude"),
                 response.getDouble("longitude"),
                 response.getString("distance"),
                 response.getString("type"),
                 response.getString("code"),
                 response.getBoolean("working"),
                 response.getString("worktime")

             )
        }


        for (branch in branchList) {

//            db.update("insert into division(nearest_distance) values(?)", GetBranchService.distance(lat, branch.latitude, lon, branch.longitude))

            var nearest_distance = GetBranchService.distance(lat, branch.latitude, lon, branch.longitude)

            val working =  GetBranchService.getWorkDayTime()



            var distanceType = 0

            if (nearest_distance < 1) {

             branch.distance = nearest_distance.toString()
                    .substring(2, 3) + "  її"

                distanceType = 0

            } else if (nearest_distance in (1.0..5.0)) {

              branch.distance =   nearest_distance.toString().substring(0,1) + " її " + nearest_distance.toString().substring(3,3) + " ї"

                distanceType = 1

            } else {
                branch.distance =   nearest_distance
                    .toBigDecimal()
                    .setScale(2, RoundingMode.UP)
                    .toDouble()
                    .toString() + " її"
                distanceType = 2
            }


           branch.working  = working
           branch.worktime = "Пн-Пт с 09:00 до 18:00"



        }

        branchList = branchList.sortedBy { it.distance.toDouble() }

        return branchList
    }

    companion object {
        fun distance(lat1: Double, lat2: Double, lon1: Double, lon2: Double): Double {
            // The math module contains a function named
            // radians which converts from degrees to radians.
            val lon1 = Math.toRadians(lon1)
            val lon2 = Math.toRadians(lon2)
            val lat1 = Math.toRadians(lat1)
            val lat2 = Math.toRadians(lat2)

            // Haversine formula
            val dlon = lon2 - lon1
            val dlat = lat2 - lat1
            val a = sin(dlat / 2).pow(2.0) + cos(lat1) * cos(lat2) * sin(dlon / 2).pow(2.0)

            val c = 2 * asin(sqrt(a))

            // Radius of earth in kilometers. Use 3956 for miles
            val r = 6371.0

            // calculate the result

            return c * r
        }



        fun getWorkDayTime(): Boolean {
            val calendar = Calendar.getInstance()
            val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)

            val weekDay = when (dayOfWeek) {

                Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY-> true
                else -> false

            }


            val currentTime = LocalTime.now()
            val formatter = DateTimeFormatter.ofPattern("hh")
            val workTime = currentTime.format(formatter)

            val time = when(workTime.toInt()) {
                in 9..18 -> true
                else -> false
            }
//            println("Current time is: ${time && weekDay}")



            return time && weekDay


        }
    }



}
