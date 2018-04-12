> 代码是kotlin代码，所以看到有些值直接调用的不要疑惑，这些直接调用的值并不是类属性实际也是调用的get和set方法

废话不多说，直接开始。

根据之前的文章，自定义ViewGroup需要重写onMeasure和onLayout方法，所以我们先来重写onMeasure方法

#### 重写onMeasure方法分为下面几步：

1. 遍历当前ViewGroup的所有子View，调用`measureChildWithMargins`方法设置每个ChildView的宽高，`measureChildWithMargins`和`measureChild`的区别是`measureChildWithMargins`同时设置了ChildView的margin值，使用`measureChildWithMargins`的前提是当前ViewGroup覆盖了`generateLayoutParams`3个方法，下面我们会说到，没有覆盖`generateLayoutParams`3个方法的话调用`measureChildWithMargins`会崩溃。
2. 遍历的时候同时记录当前ChildView的left和top值，在onLayout方法里使用这些值直接调用ChildView的layout方法，这样就不需要在onLayout方法里再次遍历计算ChildView的layout的值了，优化代码性能。
3. 遍历完以后调用`View.resolveSize`方法传入自己的宽高和宽高的`MeasureSpec`，来计算自己在不同父容器的`MeasureSpec`下的不同宽高

#### 首先定义几个类属性

```java
//记录执行onMeasure方法时ChildView左上角相对ViewGroup的坐标
//这样在onLayout方法就不需要再次计算了，提高效率
private val pointList = mutableListOf<Pair<Int, Int>>()
//行间距，在xml获取该值
private var mRowSpacing = 0
//列间距，在xml获取该值
private var mColumnSpacing = 0
```
#### 重写的`onMeasure`方法如下

1. 首先在该方法里我们先定义几个变量：

   1. 需要1个变量记录自己的宽（width）

   2. 需要1个变量记录当前行顶部坐标（相对于ViewGroup），用来最后计算该ViewGroup的高度（startY）

   3. 需要2个变量记录当前行的行宽和行高，用来判断是否需要换行（lineWidth，lineHeight）

   4. 需要1个变量记录当前ChildView的左边坐标（相对于ViewGroup）（childLeft），用来保存当前ChildView的绘制位置（left和top）
       代码如下

       ```java
       //计算的宽度
       var width = 0
       //记录当前行顶部坐标（相对于ViewGroup）
       var startY = 0
       //记录当前行宽
       var lineWidth = 0
       //记录当前行高
       var lineHeight = 0
       //当前ChildView的左边坐标（相对于ViewGroup）
       var childLeft = 0
       ```

   5. 然后我们还需要记录2个值，因为ChildView可用空间不包括上层容器的padding值，所以先定义2个值，下面会用到

      ```java
      //ViewGroup的左右padding值
      val lrPaddingUsed = paddingLeft + paddingRight
      //ViewGroup的上下padding值
      val tbPaddingUsed = paddingTop + paddingBottom
      ```


2. 开始遍历，并且记录ChildView在调用ChildView自己的layout方法时需要的left和top值，代码如下


```java
(0 until childCount).forEach { i ->
    val child = getChildAt(i)
    //GONE状态的View就不需要执行measureChild方法了，以提高效率，因为这种状态的View宽高是0(自定义View需要将GONE状态的自己的宽高设置为0)
    if (child.visibility != View.GONE) {
        //2.调用measureChildWithMargins计算子View宽高
        //因为重写了3个generateLayout方法所以这里调用measureChildWithMargins不会有异常
        measureChildWithMargins(child, widthMeasureSpec, lrPaddingUsed, heightMeasureSpec, tbPaddingUsed)
        //3.1.子View执行measure方法后该ViewGroup获取子View的getMeasuredWidth和getMeasuredHeight
        val layoutParams = child.layoutParams as MarginLayoutParams
        //记录该ChildView占用的空间
        val childWidth = layoutParams.leftMargin + child.measuredWidth + layoutParams.rightMargin
        val childHeight = layoutParams.topMargin + child.measuredHeight + layoutParams.bottomMargin
        //3.2.计算ViewGroup自己的宽高
        //第一个ChildView或者每行第一个ChildView的左边都是没有mColumnSpacing的
        //每行的最后一个ChildView也是没有mColumnSpacing的
        //第一行第一个ChildView不需要换行
        if (i == 0) {//第一行
            //第一个ChildView初始化childLeft和childTop
            //paddingLeft是ViewGroup的左边内间距
            childLeft = paddingLeft + layoutParams.leftMargin
            //paddingTop是ViewGroup的上边内间距，将第一行的顶部坐标设为paddingTop
            startY = paddingTop
            //lineWidth行宽在每行放置第一个ChildView时除了累加childWidth还需要累加ViewGroup的左右内间距
            lineWidth += lrPaddingUsed + childWidth
            //lineHeight设置为第一行第一个ChildView的高度
            lineHeight = childHeight
        } else if (lineWidth + mColumnSpacing + childWidth <= measureWidth) {
            //进入该代码块代表当前ChildView和上一个ChildView在同一行
            //所以只需要设置childLeft而不需要设置childTop
            //判断时需要mColumnSpacing是因为2个ChildView之间有列间距
            childLeft += mColumnSpacing + layoutParams.leftMargin
            lineWidth += mColumnSpacing + childWidth
            //lineHeight取最大高度
            lineHeight = Math.max(childHeight, lineHeight)
        } else {//需要换行，该ChildView放到了新行
            //换行时childLeft和第一行一样需要重新设置为ViewGroup的左边内间距加该ChildView的左外间距
            childLeft = paddingLeft + layoutParams.leftMargin
            //该行顶部坐标（相对于ViewGroup）需要累加上一行的行高和行间距
            startY += lineHeight + mRowSpacing
            //下面2个值的操作和第一行一样
            lineWidth = lrPaddingUsed + childWidth
            //lineHeight取新行第一个ChildView的高度
            lineHeight = childHeight
        }
        //添加该ChildView的left和top到集合，以便在该类的onLayout方法中调用ChildView的layout方法给该ChildView布局
        pointList.add(Pair(childLeft, startY + layoutParams.topMargin))
        //该ViewGroup的宽度取当前该ViewGroup的宽度和行宽的最大值
        width = Math.max(width, lineWidth)
        //childLeft设置为该ChildView所占空间的右边坐标
        //说明一下，每个ChildView所占空间包括了它的margin值，因为ChildView的外间距是不能显示任何控件的，外间距这部分空间是View之间的间距
        childLeft += child.measuredWidth + layoutParams.rightMargin
    }
}
```

上面的代码我把注释写的很详细，已经不需要解释什么了。。。



3. 遍历完以后，就可以该ViewGroup的宽高也就可以确定了，接下来调用`View.resolveSize`方法来计算自己在不同父容器的`MeasureSpec`下的不同宽高，代码如下

   ```java
   //4.调用resolveSize方法传入自己计算的宽高和上级ViewGroup的MeasureSpec，得到自身不同MeasureSpec下的宽高
   val resultWidth = resolveSize(width, widthMeasureSpec)
   val resultHeight = resolveSize(startY + lineHeight + paddingBottom, heightMeasureSpec)
   ```

4. 然后调用`setMeasuredDimension`方法保存自己的宽高，代码如下

   ```java
   //5.调用setMeasuredDimension保存自己的宽高
   setMeasuredDimension(resultWidth, resultHeight)
   ```

5. 到这里，`onMeasure`方法就结束了，下面我们开始onLayout方法的重写

#### 重写的`onLayout`方法如下

   这个方法就很简单了，因为在onMeasure方法里已经设置好了ChildView们的位置，让我们来看一下代码吧

   ```java
   override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
       val childCount = this.childCount
       (0 until childCount).forEach { i ->
           val child = getChildAt(i)
           //GONE状态的View就不需要执行layout方法了，以提高效率，因为这种状态的View宽高是0(自定义View需要将GONE状态的自己的宽高设置为0)
           if (child.visibility != View.GONE) {
               val pair = pointList[i]
               child.layout(pair.first, pair.second, pair.first + child.measuredWidth, pair.second + child.measuredHeight)
           }
       }
   }
   ```

然后该流式布局的主要部分就完成了


#### 重写`generateLayoutParams`的3个方法

然后我们需要重写`generateLayoutParams`的3个方法，否则在调用`measureChildWithMargins`方法的时候是会报类转换异常的，至于为什么自己看一下源码就知道了，下面直接上重写好的代码

```java
//ChildView的LayoutParams是包裹它的ViewGroup传递的，而默认传递的ViewGroup.LayoutParams是没有margin值的
//所以如果要使用margin需要重写这3个方法，ViewGroup会根据不同情况调用不同的方法的，所以最好把3个方法都重写了
override fun generateDefaultLayoutParams(): LayoutParams {
    return MarginLayoutParams(super.generateDefaultLayoutParams())
}

override fun generateLayoutParams(attrs: AttributeSet?): LayoutParams {
    return MarginLayoutParams(context, attrs)
}

override fun generateLayoutParams(p: LayoutParams?): LayoutParams {
    return MarginLayoutParams(p)
}
```


最后还有个之前提到的`mRowSpacing`和`mColumnSpacing`，这两个值在xml里设置该流式布局的行间距和列间距，我们在res/value下创建一个文件，例如叫做`attrs_flowlayout.xml`，然后添加如下代码

```xml
<resources>
    <declare-styleable name="FlowLayout">
        <attr name="rowSpacing" format="dimension"/>
        <attr name="columnSpacing" format="dimension"/>
    </declare-styleable>
</resources>
```

使用方法如下

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="horizontal"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    tools:context="liuhc.me.flowlayout.MainActivity">

    <liuhc.me.flowlayout.FlowLayout
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:background="#ff0000"
        app:rowSpacing="10dp"
        app:columnSpacing="10dp"
        android:padding="10dp">
      ...
    </liuhc.me.flowlayout.FlowLayout>
</LinearLayout>
```



至此，一个流式布局就完成了，代码提交到了github，地址：
https://github.com/ikakaxi/FlowLayout