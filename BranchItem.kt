package com.example.demo.model

data class  BranchItem(
    val pdevice:Int,
    val description:String,
    val address:String,
    val latitude:Double,
    val longitude:Double,
    var distance: String,
    val type: String,
    val code: String,
    var working: Boolean,
    var worktime: String,
)
