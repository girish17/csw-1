package csw.params.core.formats

import io.bullet.borer.derivation.{ArrayBasedCodecs, MapBasedCodecs}

object CodecTypeAlias {
//  val Codecs: MapBasedCodecs.type = MapBasedCodecs
  val Codecs: ArrayBasedCodecs.type = ArrayBasedCodecs
  lazy val isMapBased: Boolean = (Codecs: Any) match {
    case MapBasedCodecs   => true
    case ArrayBasedCodecs => false
  }
}
