import java.nio.charset.StandardCharsets
import java.util

import sbt.{File, IO, Logger}
import com.google.javascript.jscomp.{AbstractCommandLineRunner, CompilationLevel, Compiler, CompilerOptions, SourceFile, SourceMap}
import SourceMap.LocationMapping
import com.google.javascript.jscomp.CompilerOptions.LanguageMode

import scala.collection.JavaConverters._

object JsUtils {

  def assembleJs(source: File, target: File, log: Logger): Seq[File] = {

    log.info("Assembling ES6 sources using Google Closure Compiler")

    val sourceOutputFile = new File(target, "korolev-client.min.js")
    val sourceMapOutputFile = new File(target, "korolev-client.min.js.map")

    val (sourceOutput, compilationResult) = {
      val compiler = new Compiler()
      val externs = AbstractCommandLineRunner
        .getBuiltinExterns(CompilerOptions.Environment.BROWSER)
      
      val inputs = {
        val xs = source.listFiles().map { file =>
          val path = file.getAbsolutePath
          val charset = StandardCharsets.UTF_8
          SourceFile.fromFile(path, charset)
        }
        util.Arrays.asList[SourceFile](xs:_*)
      }
      val options = {
        val options = new CompilerOptions()
        options.setLanguageIn(LanguageMode.ECMASCRIPT_2015)
        options.setLanguageOut(LanguageMode.ECMASCRIPT5_STRICT)
        options.setSourceMapIncludeSourcesContent(true)
        options.setSourceMapLocationMappings(List(new LocationMapping(source.getAbsolutePath, "korolev/es6")).asJava)
        options.setSourceMapOutputPath(sourceMapOutputFile.getAbsolutePath)
        options.setEnvironment(CompilerOptions.Environment.BROWSER)

        CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options)
        options
      }
      val result = compiler.compile(externs, inputs, options)
      (compiler.toSource, result)
    }

    val sourceMapOutput = {
      val stringBuilder = new java.lang.StringBuilder()
      //stringBuilder.append(")]}")
      compilationResult.sourceMap.appendTo(stringBuilder, "korolev-client.min.js")
      stringBuilder.toString
    }

    IO.write(sourceOutputFile, "//@ sourceMappingURL=korolev-client.min.js.map\n" + sourceOutput)
    IO.write(sourceMapOutputFile, sourceMapOutput)
    Seq(sourceOutputFile, sourceMapOutputFile)
  }
}
