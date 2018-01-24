/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt

import org.scalatest._
import scala.concurrent._
import java.util.concurrent.atomic.AtomicInteger

class ServerSpec extends AsyncFlatSpec with Matchers {
  import ServerSpec._

  "server" should "start" in {
    withBuild("handshake") { temp =>
      assert(1 == 1)
    }
  }
}

object ServerSpec {
  private val serverTestBase: File = new File(".").getAbsoluteFile / "sbt" / "src" / "server-test"
  private val nextThreadId = new AtomicInteger(1)
  private val threadGroup = Thread.currentThread.getThreadGroup()

  private val threadFactory = new java.util.concurrent.ThreadFactory() {
    override def newThread(runnable: Runnable): Thread = {
      val thread =
        new Thread(threadGroup,
                   runnable,
                   s"sbt-test-server-threads-${nextThreadId.getAndIncrement}")
      // Do NOT setDaemon because then the code in TaskExit.scala in sbt will insta-kill
      // the backgrounded process, at least for the case of the run task.
      thread
    }
  }

  private val executor = new java.util.concurrent.ThreadPoolExecutor(
    0, /* corePoolSize */
    1, /* maxPoolSize, max # of servers */
    2,
    java.util.concurrent.TimeUnit.SECONDS,
    /* keep alive unused threads this long (if corePoolSize < maxPoolSize) */
    new java.util.concurrent.SynchronousQueue[Runnable](),
    threadFactory
  )

  def backgroundRun(baseDir: File, args: Seq[String]): Unit = {
    executor.execute(new Runnable {
      def run(): Unit = {
        RunFromSourceMain.run(baseDir, args)
      }
    })
  }

  def shutdown(): Unit = executor.shutdown()

  def withBuild(testBuild: String)(f: File => Future[Assertion]): Future[Assertion] = {
    val temp = IO.createTemporaryDirectory
    // IO.withTemporaryDirectory { temp =>
    IO.copyDirectory(serverTestBase / testBuild, temp / testBuild)
    withBuild(temp / testBuild)(f)
    // }
  }

  def withBuild(baseDirectory: File)(f: File => Future[Assertion]): Future[Assertion] = {
    backgroundRun(baseDirectory, Nil)
    try {
      val portfile = baseDirectory / "project" / "target" / "active.json"
      def waitForPortfile(n: Int): Unit =
        if (portfile.exists) ()
        else {
          if (n <= 0) sys.error(s"Timeout. $portfile is not found.")
          else {
            Thread.sleep(1000)
            waitForPortfile(n - 1)
          }
        }
      waitForPortfile(10)
      f(baseDirectory)
    } finally {
      shutdown()
    }
  }
}
