# Two-client OddSockets round-trip, built straight from the SDK sources.
#
# Build from the repository ROOT so the composite build (includeBuild("..") in
# demo/settings.gradle.kts) can see the parent SDK:
#
#   docker build -f demo/Dockerfile -t oddsockets-kotlin-demo .
#   docker run --rm -e ODDSOCKETS_API_KEY="ak_your_key_here" oddsockets-kotlin-demo
#
# The JDK 11 image matches the SDK's jvmToolchain(11). Compiling happens at image
# build time, so if the SDK transport does not build the image build fails - only
# a genuinely-compiling SDK produces a runnable image.
FROM gradle:8.5-jdk11

WORKDIR /app
COPY . /app

WORKDIR /app/demo

# Compile the SDK + demo and produce a runnable distribution. This is the
# acceptance gate: a broken build stops the image here.
RUN gradle --no-daemon --console=plain installDist

# Run the two-client round-trip. ODDSOCKETS_API_KEY is read from the environment.
ENTRYPOINT ["build/install/oddsockets-kotlin-demo/bin/oddsockets-kotlin-demo"]
