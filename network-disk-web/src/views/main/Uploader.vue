<template>
  <div class="uploader-panel">
    <div class="uploader-title">
      <span>上传任务</span>
      <span class="tips">（仅展示本次上传任务）</span>
    </div>
    <div class="file-list">
      <div v-for="(item, index) in fileList" class="file-item">
        <div class="upload-panel">
          <div class="file-name">
            {{ item.fileName }}
          </div>
          <div class="progress">
            <!--上传-->
            <el-progress
              :percentage="item.uploadProgress"
              v-if="
                item.status == STATUS.uploading.value ||
                item.status == STATUS.upload_seconds.value ||
                item.status == STATUS.upload_finish.value
              "
            />
          </div>
          <div class="upload-status">
            <!--图标-->
            <span
              :class="['iconfont', 'icon-' + STATUS[item.status].icon]"
              :style="{ color: STATUS[item.status].color }"
            ></span>
            <!--状态描述-->
            <span
              class="status"
              :style="{ color: STATUS[item.status].color }"
              >{{
                item.status == "fail" ? item.errorMsg : STATUS[item.status].desc
              }}</span
            >
            <!--上传中-->
            <span
              class="upload-info"
              v-if="item.status == STATUS.uploading.value"
            >
              {{ sizeTostr(item.uploadSize) }}/{{ sizeTostr(item.totalSize) }}
            </span>
          </div>
        </div>
        <div class="op">
          <!--MD5-->
          <el-progress
            type="circle"
            :width="50"
            :percentage="item.md5Progress"
            v-if="item.status == STATUS.init.value"
          />
          <div class="op-btn">
            <span v-if="item.status === STATUS.uploading.value">
              <icon
                :width="28"
                class="btn-item"
                iconName="upload"
                v-if="item.pause"
                title="上传"
                @click="startUpload(item.uid)"
              ></icon>
              <icon
                :width="28"
                class="btn-item"
                iconName="pause"
                title="暂停"
                @click="pauseUpload(item.uid)"
                v-else
              ></icon>
            </span>
            <icon
              :width="28"
              class="del btn-item"
              iconName="del"
              title="删除"
              v-if="
                item.status != STATUS.init.value &&
                item.status != STATUS.upload_finish.value &&
                item.status != STATUS.upload_seconds.value
              "
              @click="delUpload(item.uid, index)"
            ></icon>
            <icon
              :width="28"
              class="clean btn-item"
              iconName="clean"
              title="清除"
              v-if="
                item.status == STATUS.upload_finish.value ||
                item.status == STATUS.upload_seconds.value
              "
              @click="delUpload(item.uid, index)"
            ></icon>
          </div>
        </div>
      </div>
      <div v-if="fileList.length == 0">
        <NoData msg="暂无上传任务"></NoData>
      </div>
    </div>
  </div>
</template>

<script setup>
import {
  getCurrentInstance,
  onMounted,
  reactive,
  ref,
  watch,
  nextTick,
} from "vue";
import SparkMD5 from "spark-md5";
const { proxy } = getCurrentInstance();

const STATUS = {
  emptyfile: {
    value: "emptyfile",
    desc: "文件为空",
    color: "#F75000",
    icon: "close",
  },
  fail: {
    value: "fail",
    desc: "上传失败",
    color: "#F75000",
    icon: "close",
  },
  init: {
    value: "init",
    desc: "解析中",
    color: "#e6a23c",
    icon: "clock",
  },
  uploading: {
    value: "uploading",
    desc: "上传中",
    color: "#409eff",
    icon: "upload",
  },
  upload_finish: {
    value: "upload_finish",
    desc: "上传完成",
    color: "#67c23a",
    icon: "ok",
  },
  upload_seconds: {
    value: "upload_seconds",
    desc: "秒传",
    color: "#67c23a",
    icon: "ok",
  },
};

const chunkSize = 1024 * 1024 * 5; //文件分块大小
const fileList = ref([]);
const delList = ref([]);

const addFile = async (file, filePid) => {
  const fileItem = {
    file: file,
    //文件UID
    uid: file.uid,
    //md5进度
    md5Progress: 0,
    //md5值
    md5: null,
    //文件名
    fileName: file.name,
    //上传状态
    status: STATUS.init.value,
    //已上传大小
    uploadSize: 0,
    //文件总大小
    totalSize: file.size,
    //进度
    uploadProgress: 0,
    //暂停
    pause: false,
    //当前分片
    chunkIndex: 0,
    //父级ID
    filePid: filePid,
    //错误信息
    errorMsg: null,
  };
  //加入文件
  fileList.value.unshift(fileItem);
  if (fileItem.totalSize == 0) {
    fileItem.status = STATUS.emptyfile.value;
    return;
  }
  //文件MD5
  let md5FileUid = await computeMD5(fileItem);
  if (md5FileUid == null) {
    return;
  }
  uploadFile(md5FileUid);
};
defineExpose({ addFile });

//开始上传
const startUpload = (uid) => {
  let currentFile = getFileByUid(uid);
  currentFile.pause = false;
  uploadFile(uid, currentFile.chunkIndex);
};
//暂停上传
const pauseUpload = (uid) => {
  let currentFile = getFileByUid(uid);
  currentFile.pause = true;
};
//删除文件
const delUpload = (uid, index) => {
  delList.value.push(uid);
  fileList.value.splice(index, 1);
};

const emit = defineEmits(["uploadCallback"]);
const uploadFile = async (uid, chunkIndex) => {
  // 分块索引
  chunkIndex = chunkIndex ? chunkIndex : 0; // 如果chunkIndex为undefined或null，则将其设置为0
  //分片上传
  let currentFile = getFileByUid(uid); // 获取当前文件的信息
  const file = currentFile.file; // 获取文件对象
  const fileSize = currentFile.totalSize; // 获取文件大小
  const chunks = Math.ceil(fileSize / chunkSize); // 计算文件需要分成多少片
  for (let i = chunkIndex; i < chunks; i++) {  // 循环上传每一片
    // 如果该文件已经被删除，则退出循环
    let delIndex = delList.value.indexOf(uid); 
    if (delIndex != -1) {
      delList.value.splice(delIndex, 1);
      // console.log(delList.value);
      break;
    }
    currentFile = getFileByUid(uid); // 获取当前文件的信息
    if (currentFile.pause) { // 如果该文件已经暂停，则退出循环
      break;
    }
    let start = i * chunkSize;  // 计算当前片的起始位置和结束位置
    let end = start + chunkSize >= fileSize ? fileSize : start + chunkSize;
    let chunkFile = file.slice(start, end);  // 获取当前片的数据
    let uploadResult = await proxy.Request({ // 发送上传请求
      url: "/file/uploadFile",
      showLoading: false,
      dataType: "file",
      params: {
        file: chunkFile,
        fileName: file.name,
        fileMd5: currentFile.md5,
        chunkIndex: i,
        chunks: chunks,
        fileId: currentFile.fileId,
        filePid: currentFile.filePid,
      },
      showError: false,
      errorCallback: (errorMsg) => { // 如果上传失败，则将状态设置为失败，并记录错误信息
        currentFile.status = STATUS.fail.value;
        currentFile.errorMsg = errorMsg;
      },
      uploadProgressCallback: (event) => {
        let loaded = event.loaded;
        if (loaded > fileSize) {
          loaded = fileSize;
        }
        // 更新上传进度和上传大小
        currentFile.uploadSize = i * chunkSize + loaded;
        currentFile.uploadProgress = Math.floor(
          (currentFile.uploadSize / fileSize) * 100
        );
      },
    });
    if (uploadResult == null) { // 如果上传失败，则退出循环
      break;
    }
    // 更新当前文件的信息
    currentFile.fileId = uploadResult.data.fileId;
    currentFile.status = STATUS[uploadResult.data.status].value;
    currentFile.chunkIndex = i;
    if (
      uploadResult.data.status == STATUS.upload_seconds.value ||
      uploadResult.data.status == STATUS.upload_finish.value
    ) {
      currentFile.uploadProgress = 100;
      emit("uploadCallback");
      break;
    }
  }
};

/**
 * 计算文件的MD5值
 * @param {Object} fileItem - 文件对象
 * @returns {Promise} - 返回一个Promise对象，resolve时返回文件的uid，reject时返回null
 */
const computeMD5 = (fileItem) => {
  let file = fileItem.file;
  /**
   * 为了获取File对象的slice方法。这个方法用于从File对象中提取一个指定大小的块。
   * 如果浏览器支持slice方法，则使用该方法。否则，使用mozSlice或webkitSlice方法。
   */
  let blobSlice =
    File.prototype.slice ||
    File.prototype.mozSlice ||
    File.prototype.webkitSlice;
  let chunks = Math.ceil(file.size / chunkSize); // 计算文件分块数
  let currentChunk = 0; // 当前块数
  let spark = new SparkMD5.ArrayBuffer(); // 创建SparkMD5对象
  let fileReader = new FileReader(); // 创建FileReader对象
  let time = new Date().getTime(); // 获取当前时间戳
  //file.cmd5 = true;

  let loadNext = () => {
    let start = currentChunk * chunkSize; // 计算当前块的起始位置
    let end = start + chunkSize >= file.size ? file.size : start + chunkSize; // 计算当前块的结束位置
    fileReader.readAsArrayBuffer(blobSlice.call(file, start, end)); // 读取当前块的数据
  };

  loadNext();
  return new Promise((resolve, reject) => {
    let resultFile = getFileByUid(file.uid); // 获取文件对象
    fileReader.onload = (e) => {
      spark.append(e.target.result); // 将当前块的数据添加到SparkMD5对象中
      currentChunk++;
      if (currentChunk < chunks) { // 如果还有下一块，则继续读取下一块数据
        /*  console.log(
          `第${file.name},${currentChunk}分片解析完成, 开始第${
            currentChunk + 1
          } / ${chunks}分片解析`
        ); */
        let percent = Math.floor((currentChunk / chunks) * 100); // 计算进度百分比
        resultFile.md5Progress = percent; // 更新文件对象的MD5计算进度属性
        loadNext();
      } else {  // 如果所有块都已读取完毕，则计算MD5值并返回结果
        let md5 = spark.end(); // 计算MD5值
        /*  console.log(
          `MD5计算完成：${file.name} \nMD5：${md5} \n分片：${chunks} 大小:${
            file.size
          } 用时：${new Date().getTime() - time} ms`
        ); */
        spark.destroy(); //释放缓存
        resultFile.md5Progress = 100; // 更新文件对象的MD5计算进度属性为100%
        resultFile.status = STATUS.uploading.value; // 更新文件对象的状态属性为上传中状态
        resultFile.md5 = md5; // 更新文件对象的MD5值属性为计算出来的MD5值
        resolve(fileItem.uid); // 返回文件的uid
      }
    };
    fileReader.onerror = () => {
      resultFile.md5Progress = -1; // 返回文件的uid
      resultFile.status = STATUS.fail.value; // 更新文件对象的状态属性为上传失败状态
      resolve(fileItem.uid); // 返回文件的uid
    };
  }).catch((error) => {
    return null;
  });
};

//获取文件
const getFileByUid = (uid) => {
  let file = fileList.value.find((item) => {
    return item.file.uid === uid;
  });
  return file;
};

//字节转为M
const sizeTostr = (size) => {
  var data = "";
  if (size < 0.1 * 1024) {
    //如果小于0.1KB转化成B
    data = size.toFixed(2) + "B";
  } else if (size < 0.1 * 1024 * 1024) {
    //如果小于0.1MB转化成KB
    data = (size / 1024).toFixed(2) + "KB";
  } else if (size < 1024 * 1024 * 1024) {
    //如果小于1GB转化成MB
    data = (size / (1024 * 1024)).toFixed(2) + "MB";
  } else {
    //其他转化成GB
    data = (size / (1024 * 1024 * 1024)).toFixed(2) + "GB";
  }
  var sizestr = data + "";
  var len = sizestr.indexOf(".");
  var dec = sizestr.substr(len + 1, 2);
  if (dec == "00") {
    //当小数点后为00时 去掉小数部分
    return sizestr.substring(0, len) + sizestr.substr(len + 3, 2);
  }
  return sizestr;
};
</script>

<style lang="scss" scoped>
.uploader-panel {
  .uploader-title {
    border-bottom: 1px solid #ddd;
    line-height: 40px;
    padding: 0px 10px;
    font-size: 15px;
    .tips {
      font-size: 13px;
      color: rgb(169, 169, 169);
    }
  }
  .file-list {
    overflow: auto;
    padding: 10px 0px;
    min-height: calc(100vh / 2);
    max-height: calc(100vh - 120px);
    .file-item {
      position: relative;
      display: flex;
      justify-content: center;
      align-items: center;
      padding: 3px 10px;
      background-color: #fff;
      border-bottom: 1px solid #ddd;
    }
    .file-item:nth-child(even) {
      background-color: #fcf8f4;
    }
    .upload-panel {
      flex: 1;
      .file-name {
        color: rgb(64, 62, 62);
      }
      .upload-status {
        display: flex;
        align-items: center;
        margin-top: 5px;
        .iconfont {
          margin-right: 3px;
        }
        .status {
          color: red;
          font-size: 13px;
        }
        .upload-info {
          margin-left: 5px;
          font-size: 12px;
          color: rgb(112, 111, 111);
        }
      }
      .progress {
        height: 10px;
      }
    }
    .op {
      width: 100px;
      display: flex;
      align-items: center;
      justify-content: flex-end;
      .op-btn {
        .btn-item {
          cursor: pointer;
        }
        .del,
        .clean {
          margin-left: 5px;
        }
      }
    }
  }
}
</style>
