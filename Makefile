# Build/test driver for the Chisel tutorial.
# Each chapter is an INDEPENDENT sbt project (its own build.sbt), so we simply
# run sbt inside each chapter folder.
#
#   make test                 run `sbt test` in every chapter
#   make test CH=ch01-introduction   run tests for just one chapter
#   make clean                remove all build artifacts
#   make list                 list the chapter folders

CHAPTERS := $(sort $(wildcard ch*-*))

.PHONY: test clean list

list:
	@echo $(CHAPTERS)

test:
	@set -e; for d in $(if $(CH),$(CH),$(CHAPTERS)); do \
	  echo "==== $$d: sbt test ===="; \
	  ( cd $$d && sbt test ); \
	done

clean:
	@for d in $(CHAPTERS); do \
	  rm -rf $$d/target $$d/project/target $$d/project/project \
	         $$d/test_run_dir $$d/generated; \
	  rm -f $$d/*.sv $$d/*.fir $$d/*.hex $$d/*.anno.json; \
	done
	@echo "cleaned all chapters"
