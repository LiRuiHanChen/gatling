/*
 * Copyright 2011-2021 GatlingCorp (https://gatling.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.gatling.graphite.bean;

/**
 * @author lilei
 * 每个对象代表一个时间点的指标数据
 */
public class IndexStatistics {

    Long currentTime;
    scala.collection.mutable.Map<String, Long> koCountMap;
    scala.collection.mutable.Map<String, Long> activeUsersMap;

    public Long getCurrentTime() {
        return currentTime;
    }

    public void setCurrentTime(Long currentTime) {
        this.currentTime = currentTime;
    }

    public scala.collection.mutable.Map<String, Long> getKoCountMap() {
        return koCountMap;
    }

    public void setKoCountMap(scala.collection.mutable.Map<String, Long> koCountMap) {
        this.koCountMap = koCountMap;
    }

    public scala.collection.mutable.Map<String, Long> getActiveUsersMap() {
        return activeUsersMap;
    }

    public void setActiveUsersMap(scala.collection.mutable.Map<String, Long> activeUsersMap) {
        this.activeUsersMap = activeUsersMap;
    }
}
