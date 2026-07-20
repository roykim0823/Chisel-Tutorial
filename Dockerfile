# A reproducible environment for the Chisel tutorial (Ubuntu 24.04).
#
#   docker build -t chisel-tutorial .
#   docker run -it --rm chisel-tutorial            # everything preinstalled
#   docker run -it --rm -v "$PWD":/tutorial chisel-tutorial   # use your copy
#
# Inside the container:  make test         (or: cd ch01-introduction && sbt test)

FROM ubuntu:24.04

# No interactive prompts during apt installs.
ENV DEBIAN_FRONTEND=noninteractive

# Required: JDK 17 + git + make. Optional CLI tools that a few chapters use:
# verilator (alternative ChiselTest backend) and z3 (formal verification).
# sbt comes from its official apt repository. Chisel 6 bundles firtool, so no
# extra Verilog backend is needed.
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        openjdk-17-jdk-headless git make curl gnupg ca-certificates \
        verilator z3 \
    && echo "deb https://repo.scala-sbt.org/scalasbt/debian all main" \
        > /etc/apt/sources.list.d/sbt.list \
    && curl -sL "https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x2EE0EA64E40A89B84B2DF73499E82A75642AC823" \
        | gpg --dearmor -o /etc/apt/trusted.gpg.d/scalasbt-release.gpg \
    && apt-get update \
    && apt-get install -y --no-install-recommends sbt \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /tutorial
COPY . /tutorial

# Pre-download the pinned sbt launcher and the Chisel/Scala libraries into the
# shared Coursier/Ivy cache (all chapters share it), so the first real build in
# the container is fast. Build artifacts are then cleaned; the cache persists.
RUN cd ch01-introduction && sbt compile && cd .. \
    && make clean

CMD ["/bin/bash"]
