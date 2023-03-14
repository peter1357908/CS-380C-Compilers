if(WIN32 OR CYGWIN)
  set(LLVM_LINK_COMPONENTS Core Support)
endif()

add_llvm_library( sg46683-LoopAnalysis MODULE BUILDTREE_ONLY
  sg46683-LoopAnalysis.cpp

  DEPENDS
  intrinsics_gen
  PLUGIN_TOOL
  opt
  )
