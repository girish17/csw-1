from __future__ import division
from __future__ import print_function
from __future__ import unicode_literals
from __future__ import absolute_import

import codecs
import json
import logging.config
import os
import pathlib
import tmt_formatter

logging.basicConfig(level="INFO")
logger = logging.getLogger()
logger.info("This is the logger configured by `logging.basicConfig()`.")

config_file = "logging.json"
with codecs.open(config_file, "r", encoding="utf-8") as fd:
    config = json.load(fd)


dirPath = os.environ.get("TMT_LOG_HOME")
pathlib.Path(dirPath).mkdir(parents=True, exist_ok=True)
logPath = dirPath + "/python.log"
config["logging"]["handlers"]["file_handler"]["filename"]=logPath
logging.config.dictConfig(config["logging"])

