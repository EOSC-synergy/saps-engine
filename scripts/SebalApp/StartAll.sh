#!/bin/bash
SEBAL_WORK_DIR_PATH=$1
PRIVATE_KEY_PATH=$2

# installing git
sudo apt-get update
echo -e "Y\n" | sudo apt-get install git-all

SEBAL_ENGINE_LINK=https://github.com/fogbow/sebal-engine.git

cd $SEBAL_WORK_DIR_PATH

# cloning sebal-engine repository
git clone $SEBAL_ENGINE_LINK

SEBAL_ENGINE_VERSION=$(git rev-parse HEAD)

SEBAL_ENGINE_PATH=$SEBAL_WORK_DIR_PATH/$SEBAL_ENGINE_LINK

START_SCHEDULER_INFRA_SCRIPT=scripts/StartSchedulerInfra.sh
START_CRAWLER_INFRA_SCRIPT=scripts/StartCrawlerInfra.sh
START_FETCHER_INFRA_SCRIPT=scripts/StartFetcherInfra.sh
START_DB_BOOTSTRAP_SCRIPT=scripts/StartBootstrap.sh
START_SCHEDULER_SCRIPT=scripts/StartScheduler.sh
START_CRAWLER_SCRIPT=scripts/StartCrawler.sh
START_FETCHER_SCRIPT=scripts/StartFetcher.sh

START_SCHEDULER_INFRA_SCRIPT_PATH=$SEBAL_ENGINE_PATH/$START_SCHEDULER_INFRA_SCRIPT
START_CRAWLER_INFRA_SCRIPT_PATH=$SEBAL_ENGINE_PATH/$START_CRAWLER_INFRA_SCRIPT
START_FETCHER_INFRA_SCRIPT_PATH=$SEBAL_ENGINE_PATH/$START_FETCHER_INFRA_SCRIPT
START_DB_BOOTSTRAP_SCRIPT_PATH=$SEBAL_ENGINE_PATH/$START_DB_BOOTSTRAP_SCRIPT
START_SCHEDULER_SCRIPT_PATH=$SEBAL_ENGINE_PATH/$START_SCHEDULER_SCRIPT
START_CRAWLER_SCRIPT_PATH=$SEBAL_ENGINE_PATH/$START_CRAWLER_SCRIPT
START_FETCHER_SCRIPT_PATH=$SEBAL_ENGINE_PATH/$START_FETCHER_SCRIPT

cd $SEBAL_WORK_DIR_PATH

echo "Starting SEBAL Infrastructure. SEBAL-Engine version is $SEBAL_ENGINE_VERSION"

bash $START_SCHEDULER_INFRA_SCRIPT_PATH $PRIVATE_KEY_PATH $SEBAL_SCHEDULER_INFRA_SCRIPT".out" 2>&1
bash $START_CRAWLER_INFRA_SCRIPT_PATH $PRIVATE_KEY_PATH $SEBAL_CRAWLER_INFRA_SCRIPT".out" 2>&1
bash $START_FETCHER_INFRA_SCRIPT_PATH $PRIVATE_KEY_PATH $SEBAL_FETCHER_INFRA_SCRIPT".out" 2>&1

# initializing DB bootstrap
bash $START_DB_BOOTSTRAP_SCRIPT_PATH $START_DB_BOOTSTRAP_SCRIPT".out" 2>&1

# initializing scheduler, crawler and fetcher execution
bash $START_SCHEDULER_SCRIPT_PATH $START_SCHEDULER_SCRIPT".out" 2>&1
bash $START_CRAWLER_SCRIPT_PATH $START_CRAWLER_SCRIPT".out" 2>&1
bash $START_CRAWLER_SCRIPT_PATH $START_CRAWLER_SCRIPT".out" 2>&1


