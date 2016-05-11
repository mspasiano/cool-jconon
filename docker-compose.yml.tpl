jconon:
  image: docker.si.cnr.it/##{CONTAINER_ID}##
  environment:
  - JAVA_OPTS='-Xmx256m'
  - LANG=en_US.UTF-8
  - LANGUAGE=en_US:en
  - LC_ALL=en_US.UTF-8
  - SERVICE_TAGS=webapp
  - SERVICE_NAME=##{SERVICE_NAME}##
