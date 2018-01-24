lazy val root = (project in file("."))
  .settings(
    name := "handshake",
    serverConnectionType in Global := ConnectionType.Tcp,
    scalaVersion := "2.12.3",
    serverPort in Global := 5123,
  )
