import sbt.Keys._
import sbt._

object UnidocSite extends AutoPlugin {
  import sbtunidoc.{BaseUnidocPlugin, JavaUnidocPlugin, ScalaUnidocPlugin}
  import BaseUnidocPlugin.autoImport.unidoc
  import JavaUnidocPlugin.autoImport._
  import ScalaUnidocPlugin.autoImport._
  import com.typesafe.sbt.site.SitePlugin.autoImport._

  override def requires: Plugins = ScalaUnidocPlugin && JavaUnidocPlugin

  def excludeJavadoc: Set[String] = Set("internal", "scaladsl")
  def excludeScaladoc: String     = Seq("akka").mkString(":")

  override def projectSettings: Seq[Setting[_]] = Seq(
    siteSubdirName in ScalaUnidoc := "/api/scala",
    addMappingsToSiteDir(mappings in (ScalaUnidoc, packageDoc), siteSubdirName in ScalaUnidoc),
    siteSubdirName in JavaUnidoc := "/api/java",
    filterNotSources(sources in (JavaUnidoc, unidoc), excludeJavadoc),
    addMappingsToSiteDir(mappings in (JavaUnidoc, packageDoc), siteSubdirName in JavaUnidoc),
    scalacOptions in (ScalaUnidoc, unidoc) ++= Seq("-skip-packages", excludeScaladoc, "-Xfatal-warnings"),
    autoAPIMappings := true
  )

  def filterNotSources(filesKey: TaskKey[Seq[File]], subPaths: Set[String]): Setting[Task[Seq[File]]] = {
    filesKey := filesKey.value.filterNot(file => subPaths.exists(file.getAbsolutePath.contains))
  }
}

object ParadoxSite extends AutoPlugin {
  import com.typesafe.sbt.site.paradox.ParadoxSitePlugin
  import ParadoxSitePlugin.autoImport._
  import _root_.io.github.jonas.paradox.material.theme.ParadoxMaterialThemePlugin
  import ParadoxMaterialThemePlugin.autoImport._
  import com.lightbend.paradox.sbt.ParadoxPlugin.autoImport._

  val docsParentDir = "csw"

  override def requires: Plugins = ParadoxSitePlugin && ParadoxMaterialThemePlugin

  override def projectSettings: Seq[Setting[_]] =
    ParadoxMaterialThemePlugin.paradoxMaterialThemeSettings(Paradox) ++
    Seq(
      sourceDirectory in Paradox := baseDirectory.value / "src" / "main",
      sourceDirectory in (Paradox, paradoxTheme) := (sourceDirectory in Paradox).value / "_template",
      paradoxMaterialTheme in Paradox ~= {
        _.withFavicon("assets/tmt_favicon.ico")
          .withRepository(new URI("https://github.com/tmtsoftware/csw"))
      },
      paradoxProperties in Paradox ++= Map(
        "version"                → version.value,
        "scala.binaryVersion"    → scalaBinaryVersion.value,
        "scaladoc.base_url"      → s"https://tmtsoftware.github.io/$docsParentDir/${version.value}/api/scala",
        "javadoc.base_url"       → s"https://tmtsoftware.github.io/$docsParentDir/${version.value}/api/java",
        "extref.csw_js.base_url" → s"https://tmtsoftware.github.io/csw-js/$cswJsVersion/%s",
        "github.base_url"        → githubBaseUrl(version.value)
      )
    )

  // export CSW_JS_VERSION env variable which is compatible with csw
  // this represents version number of javascript docs maintained at https://github.com/tmtsoftware/csw-js
  private def cswJsVersion: String = (sys.env ++ sys.props).get("CSW_JS_VERSION") match {
    case Some(v) ⇒ v
    case None    ⇒ "0.1-SNAPSHOT"
  }

  private def githubBaseUrl(version: String) = {
    val baseRepoUrl = "https://github.com/tmtsoftware/csw/tree"
    if (version == "0.1-SNAPSHOT") s"$baseRepoUrl/master"
    else s"$baseRepoUrl/v$version"
  }
}
