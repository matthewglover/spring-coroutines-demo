package com.example.demo.features.users

import org.springframework.data.annotation.Id

data class NewUser(val name: String, val age: Int)

data class User(@Id val userId: Int, val name: String, val age: Int)
