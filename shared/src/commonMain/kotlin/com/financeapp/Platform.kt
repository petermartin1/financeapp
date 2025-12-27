package com.financeapp

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
