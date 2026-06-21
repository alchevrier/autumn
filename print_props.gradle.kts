task("printProps") {
    doLast {
        println("User: '" + project.findProperty("mavenCentralUsername") + "'")
        println("Pass: '" + project.findProperty("mavenCentralPassword") + "'")
    }
}
