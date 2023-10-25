#!/bin/bash

OUTPUT=../k1te-serverless/src/main/resources/META-INF/quarkus-preload-classes.txt
LIMIT=3

function help() {
  echo "
  Usage: ./class-preloader.sh - runs class-preloader Java project that go to AWS CloudWatch and extract Logs which have loaded class by JVM.
  Parameters:
  1) -groups (required) [here you need to specify your LogGroups' names (use SPACE as a delimiter)]
  2) -limit [here you can specify the number of LogStreams that script will use per LogGroup]
  3) -output [here you can specify the output file where found classes will be stored]
  "
}

function generate() {
  echo "Class preloader is invoked"
  IFS=', ' read -r -a array <<<"$2"
  if ((${#array[@]} < 1)); then
    echo "Invalid argument: $1, must have at least one group name"
  fi

  if [[ $3 = -limit ]]; then
    if [[ ! $4 =~ [0-9] ]]; then
      echo "$4"
      echo "limit must contain number greater than 0"
      exit 1
    fi
    limit=$4
  fi

  if [[ $5 = -output ]]; then
    if [[ ! $6 =~ \.txt ]]; then
      echo "-output arg must contain path to .txt file"
      exit 1
    fi
    output=$6
  fi

  if [ -z ${output+x} ]; then
    echo "output is unset so default value is used: ${OUTPUT}"
    output=${OUTPUT}
  fi

  if [ -z ${limit+x} ]; then
    echo "limit is unset so default value is used: ${LIMIT}"
    limit=${LIMIT}
  fi


  if mvn compile; then
    echo "Project was built successfully"
    mvn exec:java -DgroupNames=\""${array[*]}"\" -Doutput="${output}" -Dlimit="${limit}"
    exit 0
  fi
  exit 1
}

case "$1" in
-groups)
  generate "$@"
  ;;
help)
  help
  ;;
*)
  help
  ;;
esac
shift
