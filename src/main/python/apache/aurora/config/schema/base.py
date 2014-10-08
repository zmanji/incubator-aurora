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

# Disable checkstyle for this entire file as it is a pystachio schema.
# checkstyle: noqa

from apache.thermos.config.schema import *


# TODO(wickman) Bind {{mesos.instance}} to %shard_id%
class MesosContext(Struct):
  # The instance id (i.e. replica id, shard id) in the context of a task
  instance    = Required(Integer)


class UpdateConfig(Struct):
  batch_size                  = Default(Integer, 1)
  restart_threshold           = Default(Integer, 60)
  watch_secs                  = Default(Integer, 45)
  max_per_shard_failures      = Default(Integer, 0)
  max_total_failures          = Default(Integer, 0)
  rollback_on_failure         = Default(Boolean, True)
  wait_for_batch_completion   = Default(Boolean, False)


class HealthCheckConfig(Struct):
  initial_interval_secs    = Default(Float, 15.0)
  interval_secs            = Default(Float, 10.0)
  timeout_secs             = Default(Float, 1.0)
  max_consecutive_failures = Default(Integer, 0)


class Announcer(Struct):
  primary_port = Default(String, 'http')

  # Portmap can either alias two ports together, e.g.
  #   aurora <= http
  # Or it can be used to alias static ports to endpoints, e.g.
  #   http <= 80
  #   https <= 443
  #   aurora <= https
  portmap      = Default(Map(String, String), {
    'aurora': '{{primary_port}}'
  })


# The executorConfig populated inside of TaskConfig.
class MesosTaskInstance(Struct):
  task                       = Required(Task)
  instance                   = Required(Integer)
  role                       = Required(String)
  announce                   = Announcer
  environment                = Required(String)
  health_check_config        = Default(HealthCheckConfig, HealthCheckConfig())


class MesosJob(Struct):
  name          = Default(String, '{{task.name}}')
  role          = Required(String)
  contact       = String
  cluster       = Required(String)
  environment   = Required(String)
  instances     = Default(Integer, 1)
  task          = Required(Task)
  announce      = Announcer

  cron_schedule = String
  cron_collision_policy = Default(String, "KILL_EXISTING")

  update_config = Default(UpdateConfig, UpdateConfig())

  constraints                = Map(String, String)
  service                    = Default(Boolean, False)
  max_task_failures          = Default(Integer, 1)
  production                 = Default(Boolean, False)
  priority                   = Default(Integer, 0)
  health_check_config        = Default(HealthCheckConfig, HealthCheckConfig())
  task_links                 = Map(String, String)

  enable_hooks = Default(Boolean, False)  # enable client API hooks; from env python-list 'hooks'


Job = MesosJob
Service = Job(service = True)
