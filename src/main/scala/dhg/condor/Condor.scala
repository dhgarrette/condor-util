package dhg.condor

import dhg.util.CollectionUtil._
import dhg.util.FileUtil._
import dhg.util.StringUtil._
import dhg.util.Pattern._

/**
 * target/start dhg.condor.CondorFromFile runsfile
 * target/start dhg.condor.CondorFromFile rerun runsfile name
 */
class Condor(
  stagingDir: String,
  memory: Int = 4000) {

  def makeNamed(classnamesAndArgs: Vector[(String, String)]): Unit = {
    val digits = math.log10(classnamesAndArgs.size).toInt + 1
    makeWithNames(classnamesAndArgs.mapt {
      case (c, a) => (c, a, (c +: a.split("\\s+")).mkString("_"))
    })
  }

  def makeNumbered(classnamesAndArgs: Vector[(String, String)]): Unit = {
    val digits = math.log10(classnamesAndArgs.size).toInt + 1
    makeWithNames(classnamesAndArgs.zipWithIndex.map {
      case ((c, a), i) => (c, a, i.toString.padLeft(digits, "0"))
    })
  }

  def makeWithNames(classnamesArgsAndFilenames: Vector[(String, String, String)]): Unit = {
    val workingDir = File("").getAbsolutePath

    writeUsing(File(stagingDir, "main.config")) { w =>
      w.writeLine("universe = vanilla")
      w.writeLine("getenv = True")
      w.writeLine("")
      w.writeLine("Executable = /bin/sh")
      w.writeLine("")
      w.writeLine(s"""Requirements = InMastodon && (Memory >= $memory) && (ARCH == "X86_64")""")
      w.writeLine("+Group   = \"GRAD\"")
      w.writeLine("+Project = \"AI_ROBOTICS\"")
      w.writeLine("+ProjectDescription = \"description\"")
      w.writeLine("")
      w.writeLine(s"Initialdir = $workingDir")
      w.writeLine("")

      for ((classname, args, subscriptFilename) <- classnamesArgsAndFilenames) {
        val subscriptPath = pathjoin(stagingDir, subscriptFilename)

        println(subscriptPath)
        writeUsing(File(subscriptPath + ".sh")) { w =>
          w.writeLine("#! /bin/sh")
          w.writeLine(s"(cd $workingDir && exec $workingDir/target/start $classname $args)")
        }

        w.writeLine(s"Log = $subscriptPath.log")
        w.writeLine(s"Output = $subscriptPath.out")
        w.writeLine(s"Error = $subscriptPath.err")
        w.writeLine(s"Arguments = $subscriptPath.sh")
        w.writeLine("")
        w.writeLine("Queue")
        w.writeLine("")
      }
    }

    writeUsing(File(stagingDir, "main.sh")) { w =>
      w.writeLine(s"/lusr/opt/condor/bin/condor_submit ${pathjoin(stagingDir, "main.config")}")
    }
    println("sh " + pathjoin(stagingDir, "main.sh"))
  }

}

object CondorFromFile {
  def main(args: Array[String]): Unit = {
    args.head match {
      case "rerun" =>
        val Seq(_, filename, name) = args.toList
        val Seq(classname, argstring) = name.lsplit("_", 2)
        new Condor(pathjoin("condorfiles", filename.split("\\.").head))
          .makeNamed(Vector((classname, argstring.replace("_", " "))))
      case _ =>
        val Seq(filename) = args.toList
        val lines = File(filename).readLines.toVector
        new Condor(pathjoin("condorfiles", filename.split("\\.").head))
          .makeNamed(lines.filter(_.nonEmpty).map { line =>
            val Seq(_, classname, argstring) = line.lsplit("\\s+", 3)
            (classname, argstring)
          })
    }
  }
}
