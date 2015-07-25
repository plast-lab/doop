# Placeholder
all:

# Unix tools
INSTALL     := install
MKDIR       := mkdir -p
SED         := sed
RM          := rm -f

# For debugging
QUIET       := @
print-%:
	@echo $*=$($*)


# Include blox routines
include blox.mk

# Logic output directory
logic.outdir := ../build/logic

$(logic.outdir):
	$(MKDIR) $@

# # Directory depth
# LEVEL := ../..

# Projects (add directory here to enable it)
project.files := $(shell find -type f -name '*.project')
projects      := $(patsubst ./%.project,%,$(project.files))


#-----------------------------------
# Phony targets
#-----------------------------------

all:

force:

clean:
	$(RM) -r $(logic.outdir)

.PHONY: all clean force



#--------------------
# [Schema Project]
#--------------------

schema.proj := schema.project
schema.src  := $(filter-out %macros.logic,$(wildcard facts/*.logic))
schema.src  += $(wildcard core/*-declarations.logic)
schema.pre  := core/context-sensitivity-declarations.logic

#--------------------------
# [Must Point-to Project]
#--------------------------

must-point-to.proj := must-point-to.project
must-point-to.src  := $(wildcard analyses/must-point-to/*.logic)
must-point-to.src  += $(wildcard addons/cfg-analysis/*.logic)
must-point-to.dir  := analyses/must-point-to
must-point-to.pre  := $(must-point-to.dir)/analysis.logic

#------------------------------
#  Project Creation Template
#------------------------------

define project_template

$1.proj    ?= $1.project
$1.src     ?= $(wildcard $1/*.logic)
$1.outdir  := $(logic.outdir)/$1
$1.compile := $$($1.outdir)/.ckeep
# $1.deps   := $1/deps.mk


# Create output directory
$$($1.outdir): | $(logic.outdir)
	$(MKDIR) $$@

# # Auto-generate project dependencies
# export PATH := $(LEVEL)/bin:$(PATH)
# $$($1.deps): $$($1.proj) | $$($1.outdir)
# 	project-dependencies.sh . $$< $$($1.ph) > $$@


# # When a single `.project' file changes, *all* project-dependency
# # Makefile modules must be renewed, since their generation involves
# # reading all `.project' files to acquire their /project IDs/.

# $$($1.deps): $(project.files)


# # Include deps.mk Makefile segment
# -include $$($1.deps)

$1.preout = $$($1.pre:%.logic=%-preprocessed.logic)

# Files that need preprocessing
.INTERMEDIATE: $$($1.preout)
$$($1.preout): %-preprocessed.logic: %.logic
	cpp -P $$< $$@

.PHONY: $1.preprocess
$1.preprocess: $$($1.preout)

# Compile project
$$($1.compile): $$($1.proj) $$($1.src) $$($1.pre) $1.preprocess | $$($1.outdir)
	$(call compile-datalog-project, $$<,$$(@D))
	$(QUIET) $(INSTALL) -m 0644 $$< $$(@D)
	$(QUIET) od -vAn -N4 -tu4 < /dev/urandom > $$@


# Project build target
.PHONY: $1
$1: $$($1.compile)

# Project clean targets
.PHONY: $1.clean
$1.clean:
	$(RM) -r $$($1.outdir)

# .PHONY: $1.clean-deps
# $1.clean-deps:
# 	$(RM) $$($1.deps)

# .PHONY: $1.cleanall
# $1.cleanall: $1.clean $1.clean-deps

# Phony target dependencies
all:        $1
clean:      $1.clean
# clean-deps: $1.clean-deps
# cleanall:   $1.cleanall

endef

# !! Generate rules per project !!
$(foreach project,$(projects),$(eval $(call project_template,$(project))))



$(must-point-to.compile) : export LB_LIBRARY_PATH := $(schema.outdir)
$(must-point-to.compile) : $(schema.compile)
