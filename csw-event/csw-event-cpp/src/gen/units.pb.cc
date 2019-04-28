// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: units.proto

#include "units.pb.h"

#include <algorithm>

#include <google/protobuf/stubs/common.h>
#include <google/protobuf/stubs/port.h>
#include <google/protobuf/io/coded_stream.h>
#include <google/protobuf/wire_format_lite_inl.h>
#include <google/protobuf/descriptor.h>
#include <google/protobuf/generated_message_reflection.h>
#include <google/protobuf/reflection_ops.h>
#include <google/protobuf/wire_format.h>
// This is a temporary google only hack
#ifdef GOOGLE_PROTOBUF_ENFORCE_UNIQUENESS
#include "third_party/protobuf/version.h"
#endif
// @@protoc_insertion_point(includes)

namespace csw_protobuf {
}  // namespace csw_protobuf
namespace protobuf_units_2eproto {
void InitDefaults() {
}

const ::google::protobuf::EnumDescriptor* file_level_enum_descriptors[1];
const ::google::protobuf::uint32 TableStruct::offsets[1] = {};
static const ::google::protobuf::internal::MigrationSchema* schemas = NULL;
static const ::google::protobuf::Message* const* file_default_instances = NULL;

void protobuf_AssignDescriptors() {
  AddDescriptors();
  AssignDescriptors(
      "units.proto", schemas, file_default_instances, TableStruct::offsets,
      NULL, file_level_enum_descriptors, NULL);
}

void protobuf_AssignDescriptorsOnce() {
  static ::google::protobuf::internal::once_flag once;
  ::google::protobuf::internal::call_once(once, protobuf_AssignDescriptors);
}

void protobuf_RegisterTypes(const ::std::string&) GOOGLE_PROTOBUF_ATTRIBUTE_COLD;
void protobuf_RegisterTypes(const ::std::string&) {
  protobuf_AssignDescriptorsOnce();
}

void AddDescriptorsImpl() {
  InitDefaults();
  static const char descriptor[] GOOGLE_PROTOBUF_ATTRIBUTE_SECTION_VARIABLE(protodesc_cold) = {
      "\n\013units.proto\022\014csw_protobuf\032\025scalapb/sca"
      "lapb.proto*\373\004\n\007PbUnits\022\013\n\007NoUnits\020\000\022\014\n\010a"
      "ngstrom\020\001\022\n\n\006arcmin\020\002\022\n\n\006arcsec\020\003\022\007\n\003day"
      "\020\004\022\n\n\006degree\020\005\022\n\n\006elvolt\020\006\022\010\n\004gram\020\007\022\010\n\004"
      "hour\020\010\022\t\n\005hertz\020\t\022\t\n\005joule\020\n\022\n\n\006kelvin\020\013"
      "\022\014\n\010kilogram\020\014\022\r\n\tkilometer\020\r\022\t\n\005liter\020\016"
      "\022\t\n\005meter\020\017\022\013\n\007marcsec\020\020\022\016\n\nmillimeter\020\021"
      "\022\017\n\013millisecond\020\022\022\n\n\006micron\020\023\022\016\n\nmicrome"
      "ter\020\024\022\n\n\006minute\020\025\022\n\n\006newton\020\026\022\n\n\006pascal\020"
      "\027\022\n\n\006radian\020\030\022\n\n\006second\020\031\022\010\n\004sday\020\032\022\r\n\ts"
      "teradian\020\033\022\017\n\013microarcsec\020\034\022\010\n\004volt\020\035\022\010\n"
      "\004watt\020\036\022\010\n\004week\020 \022\010\n\004year\020!\022\013\n\007coulomb\020\""
      "\022\016\n\ncentimeter\020#\022\007\n\003erg\020$\022\006\n\002au\020%\022\n\n\006jan"
      "sky\020&\022\r\n\tlightyear\020\'\022\007\n\003mag\020(\022\007\n\003cal\020)\022\010"
      "\n\004foot\020*\022\010\n\004inch\020+\022\t\n\005pound\020,\022\010\n\004mile\020-\022"
      "\t\n\005ounce\020.\022\010\n\004yard\020/\022\013\n\007encoder\0200\022\t\n\005cou"
      "nt\0201\022\007\n\003pix\0202\032!\342\?\036\032\034csw.params.core.mode"
      "ls.Unitsb\006proto3"
  };
  ::google::protobuf::DescriptorPool::InternalAddGeneratedFile(
      descriptor, 696);
  ::google::protobuf::MessageFactory::InternalRegisterGeneratedFile(
    "units.proto", &protobuf_RegisterTypes);
  ::protobuf_scalapb_2fscalapb_2eproto::AddDescriptors();
}

void AddDescriptors() {
  static ::google::protobuf::internal::once_flag once;
  ::google::protobuf::internal::call_once(once, AddDescriptorsImpl);
}
// Force AddDescriptors() to be called at dynamic initialization time.
struct StaticDescriptorInitializer {
  StaticDescriptorInitializer() {
    AddDescriptors();
  }
} static_descriptor_initializer;
}  // namespace protobuf_units_2eproto
namespace csw_protobuf {
const ::google::protobuf::EnumDescriptor* PbUnits_descriptor() {
  protobuf_units_2eproto::protobuf_AssignDescriptorsOnce();
  return protobuf_units_2eproto::file_level_enum_descriptors[0];
}
bool PbUnits_IsValid(int value) {
  switch (value) {
    case 0:
    case 1:
    case 2:
    case 3:
    case 4:
    case 5:
    case 6:
    case 7:
    case 8:
    case 9:
    case 10:
    case 11:
    case 12:
    case 13:
    case 14:
    case 15:
    case 16:
    case 17:
    case 18:
    case 19:
    case 20:
    case 21:
    case 22:
    case 23:
    case 24:
    case 25:
    case 26:
    case 27:
    case 28:
    case 29:
    case 30:
    case 32:
    case 33:
    case 34:
    case 35:
    case 36:
    case 37:
    case 38:
    case 39:
    case 40:
    case 41:
    case 42:
    case 43:
    case 44:
    case 45:
    case 46:
    case 47:
    case 48:
    case 49:
    case 50:
      return true;
    default:
      return false;
  }
}


// @@protoc_insertion_point(namespace_scope)
}  // namespace csw_protobuf
namespace google {
namespace protobuf {
}  // namespace protobuf
}  // namespace google

// @@protoc_insertion_point(global_scope)
