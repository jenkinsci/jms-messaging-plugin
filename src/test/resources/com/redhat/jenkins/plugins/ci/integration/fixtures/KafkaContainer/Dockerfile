FROM apache/kafka:4.0.0

# Force the built fixture image to own its own committed layer. A Dockerfile that
# contains only a FROM line produces an image that borrows the base image's
# snapshot instead of committing one of its own. On Docker's containerd image
# store, that borrowed snapshot can be garbage-collected out from under the
# derived tag, making a later `docker run` fail with
# "NotFound: parent snapshot <digest> does not exist" (exit code 125).
# Adding a real build step gives the image its own snapshot chain and avoids this.
RUN true
