### ButterKnifeRemove使用指南

照着图片走就行

#### 1.下载github项目打开



<img src=".\step1.png" alt="step1" style="zoom: 200%;" />

#### 2.寻找安装地址

<img src=".\step2.png" style="zoom:150%;" />

<img src=".\step3.png" alt="step1" style="zoom:150%;" />

点击左侧复制，就将地址复制到剪贴板了

<img src=".\step4.png" style="zoom:150%;" />

#### 3.更换地址

<img src=".\step1_1.png" style="zoom:150%;" />



#### 4.点击右上交同步图标

<img src=".\step5.png" style="zoom:150%;" />

等待同步完成

#### 5.生成jar包

<img src=".\step6.png" style="zoom:150%;" />

#### 6.找到jar包位置

<img src=".\step7.png" style="zoom:150%;" />

#### 7.打开Android Studio 安装插件



<img src=".\step9.png" style="zoom:150%;" />

#### 8.在App gradle 增加  viewbinding



<img src=".\step10.png" style="zoom:150%;" />



#### 9.**生成 ViewBinding 相关的类**



在项目目录下执行 `./gradlew dataBindingGenBaseClassesDebug`   就会生成 ViewBinding 相关的类与映射文件



#### 10.进行转换操作



<img src=".\step10.png" style="zoom:150%;" />

我把jar包放到目录里了，需要的话可以下载项目

**以上是本人的使用记录，虽然移除了黄油刀，但是没有生成viewbingding,后续再研究**

