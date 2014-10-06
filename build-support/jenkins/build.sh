#!/usr/bin/env bash
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# Jenkins build script used with builds at http://builds.apache.org
set -ex
date

# Run all Java tests
./gradlew -Pq clean build --stacktrace

# Run all Python tests
# Setting the timeout value to 1 minute ensures package fetches from PyPI do not
# fail on Apache Jenkins.
./pants build --timeout=60 src/test/python:all -vxs

# Run Python style checks
./build-support/python/isort-check
./build-support/python/checkstyle-check src
