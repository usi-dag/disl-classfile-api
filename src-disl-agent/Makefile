# Import local variable definitions
-include Makefile.local

INCLUDE_DIR ?= ../src-jdk-include
LIBRARY_BASE ?= dislagent

ifeq ($(OS),Windows_NT)
    ifeq (,$(findstring /cygdrive/,$(PATH)))
        $(error Building on the Windows platform is only supported under Cygwin)
    endif

    HOST_ENV := Cygwin
else
    HOST_ENV := $(shell uname -s)
    HOST_CPU := $(shell uname -m)
endif


TARGET_ENV ?= $(HOST_ENV)
TARGET_CPU ?= $(HOST_CPU)

LIBRARY_WINDOWS = $(LIBRARY_BASE).dll
LIBRARY_MACOSX = lib$(LIBRARY_BASE).jnilib
LIBRARY_LINUX = lib$(LIBRARY_BASE).so

ifneq ($(HOST_ENV),$(TARGET_ENV))
    ifeq (,$(filter MinGW DarwinX,$(TARGET_ENV)))
        $(error Cross-compiling is only supported for TARGET_ENV=MinGW or TARGET_ENV=DarwinX)
    endif
endif


# Default compiler and linker flags to produce a shared library.
# These should be redefined for platform-specific toolchains.

LD_SHARED := -shared
CC_SHARED := -shared

ifneq (,$(filter MinGW Cygwin,$(TARGET_ENV)))
    JDK_TARGET := windows
    LIB_PREFIX :=
    LIB_SUFFIX := .dll

    ifeq (MinGW,$(TARGET_ENV))
        CC := $(TARGET_CPU)-w64-mingw32-gcc
        LD := $(TARGET_CPU)-w64-mingw32-ld

        CFLAGS += -DMINGW -mthreads -fPIC
        CFLAGS_WHOLE := -flto -fwhole-program

        LDFLAGS += --kill-at -output-def $(LIBRARY).def
        LIBDIRS += -L=/mingw/lib -L$(dir $(shell $(CC) -print-libgcc-file-name))
        LIBS += -lws2_32 -lmingwex -lmingw32 -lcrtdll -lmsvcrt -lkernel32 -lgcc
    endif

else ifneq (,$(filter DarwinX Darwin,$(TARGET_ENV)))
    JDK_TARGET := macosx
    LIB_PREFIX := lib
    LIB_SUFFIX := .jnilib

    ifeq (DarwinX,$(TARGET_ENV))
        CC := darwinx-gcc
        LD := darwinx-ld
        CFLAGS += -DDARWINX
    else
        LD_SHARED := -dylib
    endif

else ifeq (Linux,$(TARGET_ENV))
    JDK_TARGET := linux
    LIB_PREFIX := lib
    LIB_SUFFIX := .so

    #
    # Despite the GCC documentation saying that -flto should not be used with
    # -fwhole-program, the option needs to be there, otherwise GCC produces a
    # library with undefined symbols (bypass bytecode) despite them being present.
    #
    CFLAGS_WHOLE := -flto -fwhole-program

    ifeq (,$(filter $(TARGET_CPU),x86_64 ppc64))
        CFLAGS += -m32
        LDFLAGS += -melf_i386
    endif
else
    $(error Target environment $(TARGET_ENV) is not supported)
endif


ifeq (x86_64,$(TARGET_CPU))
    BUILD_DIR := $(JDK_TARGET)-x86_64
else ifeq (ppc64,$(TARGET_CPU))
    BUILD_DIR := $(JDK_TARGET)-ppc64
else
    BUILD_DIR := $(JDK_TARGET)-x86
endif

#
# TARGET_DIR defaults to BUILD_DIR, but it can be overriden from outside.
# However, the Makefile is not responsible for creating it, i.e., it has to exist.
# BUILD_DIR, on the other hand, is created by the Makefile.
#
TARGET_DIR ?= $(BUILD_DIR)


LIBRARY_NAME := $(LIB_PREFIX)$(LIBRARY_BASE)$(LIB_SUFFIX)
LIBRARY := $(TARGET_DIR)/$(LIBRARY_NAME)


# Source and object files needed to create the library
SOURCES = bytecode.c common.c jvmtiutil.c connection.c \
	connpool.c msgchannel.c network.c classparser.c \
	dislserver.pb-c.c protobuf-c.c dislagent.c

HEADERS = $(wildcard *.h) codeflags.h dislserver.pb-c.h
GENSRCS = bytecode.c codeflags.h \
	dislserver.pb-c.c dislserver.pb-c.h

OBJECTS = $(addprefix $(BUILD_DIR)/,$(SOURCES:%.c=%.o))
SRCDEPS = $(SOURCES:%.c=%.d)


# Base options depending on the build
ifeq (,$(DEBUG))
    CFLAGS += -O3 -DNDEBUG
else
    CFLAGS += -g3 -DDEBUG
endif

# Generate position independent code
CFLAGS += -fPIC

# Path to JNI and JVMTI includes
CFLAGS += -I$(INCLUDE_DIR) -I$(INCLUDE_DIR)/$(JDK_TARGET)

# Use GNU extensions and warn on most things
CFLAGS += -std=gnu99 -W -Wall -Wextra -Wno-unused-parameter

# Hide most symbols except those exported
CFLAGS_PARTS += -fvisibility=hidden


# Derive GCC flags for linker from LDFLAGS
COMMA := ,
CFLAGS_LD := $(CFLAGS_LD) $(addprefix -Wl$(COMMA),$(LDFLAGS))

#
# Link non-MinGW targets with the pthread library.
#
ifneq (MinGW,$(TARGET_ENV))
    LIBS += -lpthread
endif


# Fix-up quirks before first target
-include Makefile.quirks


# Default goal

.PHONY: agent
agent: $(GENSRCS) $(BUILD_DIR) $(LIBRARY)


# Convenience targets

.PHONY: whole
whole:
	@$(MAKE) WHOLE=1

.PHONY: debug
debug:
	@$(MAKE) DEBUG=1


# Generated files

bytecode.c codeflags.h:
	ant -f ../build.xml prepare-disl-agent


# Output directory for compiled object files

$(BUILD_DIR):
	mkdir $@


# Compilation and linking targets

ifneq (,$(WHOLE))

CFLAGS += $(CFLAGS_WHOLE) -DWHOLE

$(LIBRARY): $(HEADERS) $(SOURCES)
	$(CC) $(CC_SHARED) $(CFLAGS) $(TARGET_ARCH) $(CFLAGS_LD) $(SOURCES) $(LIBS) $(OUTPUT_OPTION)

else

CFLAGS += $(CFLAGS_PARTS)

$(LIBRARY): $(OBJECTS)
	$(LD) $(LD_SHARED) $(LDFLAGS) $(TARGET_ARCH) $(LIBDIRS) $(OBJECTS) $(LIBS) $(OUTPUT_OPTION)

$(BUILD_DIR)/%.o: %.c
	$(CC) $(CFLAGS) $(TARGET_ARCH) -c $? $(OUTPUT_OPTION)

%.d: %.c
	@$(CC) -MM $< | sed 's,\($*\)\.o[ :]*,\1.o $@ : ,g' > $@

-include $(SRCDEPS)

endif


# Cleanup targets

.PHONY: clean
clean:
	-rm -f $(OBJECTS)
	-rm -f $(SRCDEPS)

.PHONY: cleanall
cleanall: clean
	-rm -rf $(BUILD_DIR)

.PHONY: pristine
pristine: cleanall
	-rm -f $(GENSRCS)
