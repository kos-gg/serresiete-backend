package com.kos.acceptance

import io.cucumber.java.Before

class Hooks {

    @Before
    fun resetDatabase() {
        SharedInfrastructure.resetDatabase()
    }
}
