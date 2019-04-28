// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: units.proto

#ifndef PROTOBUF_INCLUDED_units_2eproto
#define PROTOBUF_INCLUDED_units_2eproto

#include <string>

#include <google/protobuf/stubs/common.h>

#if GOOGLE_PROTOBUF_VERSION < 3006001
#error This file was generated by a newer version of protoc which is
#error incompatible with your Protocol Buffer headers.  Please update
#error your headers.
#endif
#if 3006001 < GOOGLE_PROTOBUF_MIN_PROTOC_VERSION
#error This file was generated by an older version of protoc which is
#error incompatible with your Protocol Buffer headers.  Please
#error regenerate this file with a newer version of protoc.
#endif

#include <google/protobuf/io/coded_stream.h>
#include <google/protobuf/arena.h>
#include <google/protobuf/arenastring.h>
#include <google/protobuf/generated_message_table_driven.h>
#include <google/protobuf/generated_message_util.h>
#include <google/protobuf/inlined_string_field.h>
#include <google/protobuf/metadata.h>
#include <google/protobuf/repeated_field.h>  // IWYU pragma: export
#include <google/protobuf/extension_set.h>  // IWYU pragma: export
#include <google/protobuf/generated_enum_reflection.h>
#include "scalapb/scalapb.pb.h"
// @@protoc_insertion_point(includes)
#define PROTOBUF_INTERNAL_EXPORT_protobuf_units_2eproto 

namespace protobuf_units_2eproto {
// Internal implementation detail -- do not use these members.
struct TableStruct {
  static const ::google::protobuf::internal::ParseTableField entries[];
  static const ::google::protobuf::internal::AuxillaryParseTableField aux[];
  static const ::google::protobuf::internal::ParseTable schema[1];
  static const ::google::protobuf::internal::FieldMetadata field_metadata[];
  static const ::google::protobuf::internal::SerializationTable serialization_table[];
  static const ::google::protobuf::uint32 offsets[];
};
void AddDescriptors();
}  // namespace protobuf_units_2eproto
namespace csw_protobuf {
}  // namespace csw_protobuf
namespace csw_protobuf {

enum PbUnits {
  NoUnits = 0,
  angstrom = 1,
  arcmin = 2,
  arcsec = 3,
  day = 4,
  degree = 5,
  elvolt = 6,
  gram = 7,
  hour = 8,
  hertz = 9,
  joule = 10,
  kelvin = 11,
  kilogram = 12,
  kilometer = 13,
  liter = 14,
  meter = 15,
  marcsec = 16,
  millimeter = 17,
  millisecond = 18,
  micron = 19,
  micrometer = 20,
  minute = 21,
  newton = 22,
  pascal = 23,
  radian = 24,
  second = 25,
  sday = 26,
  steradian = 27,
  microarcsec = 28,
  volt = 29,
  watt = 30,
  week = 32,
  year = 33,
  coulomb = 34,
  centimeter = 35,
  erg = 36,
  au = 37,
  jansky = 38,
  lightyear = 39,
  mag = 40,
  cal = 41,
  foot = 42,
  inch = 43,
  pound = 44,
  mile = 45,
  ounce = 46,
  yard = 47,
  encoder = 48,
  count = 49,
  pix = 50,
  PbUnits_INT_MIN_SENTINEL_DO_NOT_USE_ = ::google::protobuf::kint32min,
  PbUnits_INT_MAX_SENTINEL_DO_NOT_USE_ = ::google::protobuf::kint32max
};
bool PbUnits_IsValid(int value);
const PbUnits PbUnits_MIN = NoUnits;
const PbUnits PbUnits_MAX = pix;
const int PbUnits_ARRAYSIZE = PbUnits_MAX + 1;

const ::google::protobuf::EnumDescriptor* PbUnits_descriptor();
inline const ::std::string& PbUnits_Name(PbUnits value) {
  return ::google::protobuf::internal::NameOfEnum(
    PbUnits_descriptor(), value);
}
inline bool PbUnits_Parse(
    const ::std::string& name, PbUnits* value) {
  return ::google::protobuf::internal::ParseNamedEnum<PbUnits>(
    PbUnits_descriptor(), name, value);
}
// ===================================================================


// ===================================================================


// ===================================================================

#ifdef __GNUC__
  #pragma GCC diagnostic push
  #pragma GCC diagnostic ignored "-Wstrict-aliasing"
#endif  // __GNUC__
#ifdef __GNUC__
  #pragma GCC diagnostic pop
#endif  // __GNUC__

// @@protoc_insertion_point(namespace_scope)

}  // namespace csw_protobuf

namespace google {
namespace protobuf {

template <> struct is_proto_enum< ::csw_protobuf::PbUnits> : ::std::true_type {};
template <>
inline const EnumDescriptor* GetEnumDescriptor< ::csw_protobuf::PbUnits>() {
  return ::csw_protobuf::PbUnits_descriptor();
}

}  // namespace protobuf
}  // namespace google

// @@protoc_insertion_point(global_scope)

#endif  // PROTOBUF_INCLUDED_units_2eproto
