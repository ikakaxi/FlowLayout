package liuhc.me.flowlayout

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup

/**
 * 描述:流式布局
 * 作者:liuhc
 * 创建日期：2018/3/31
 *
 * email: lhc968@163.com
 */
class FlowLayout : ViewGroup {

    //记录执行onMeasure方法时ChildView左上角相对ViewGroup的坐标
    //这样在onLayout方法就不需要再次计算了，提高效率
    private val pointList = mutableListOf<Pair<Int, Int>>()
    //行间距，在xml获取该值
    private var mRowSpacing = 0
    //列间距，在xml获取该值
    private var mColumnSpacing = 0

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        val mTypedArray = context.obtainStyledAttributes(attrs, R.styleable.FlowLayout)
        mRowSpacing = mTypedArray.getDimensionPixelSize(R.styleable.FlowLayout_rowSpacing, 0)
        mColumnSpacing = mTypedArray.getDimensionPixelSize(R.styleable.FlowLayout_columnSpacing, 0)
        mTypedArray.recycle()
    }

    /**
     * 1.重写onMeasure
     *
     * 该方法需要做这些事情：
     * - 需要1个变量记录自己的宽（width）
     * - 需要1个变量记录当前行顶部坐标（相对于ViewGroup），用来最后计算该ViewGroup的高度（startY）
     * - 需要2个变量记录当前行的行宽和行高，用来判断是否需要换行（lineWidth，lineHeight）
     * - 需要1个变量记录当前ChildView的左边坐标（相对于ViewGroup）（childLeft），用来保存当前ChildView的绘制位置（left和top）
     * 该方法还用到了mRowSpacing和mColumnSpacing，这两个值分辨用来设置该ViewGroup的行间距和列间距
     */
    @SuppressLint("DrawAllocation")
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        //上层ViewGroup建议的宽度
        val measureWidth = View.MeasureSpec.getSize(widthMeasureSpec)
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
        //ViewGroup的左右padding值
        val lrPaddingUsed = paddingLeft + paddingRight
        //ViewGroup的上下padding值
        val tbPaddingUsed = paddingTop + paddingBottom
        val childCount = this.childCount
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
        //4.调用resolveSize方法传入自己计算的宽高和上级ViewGroup的MeasureSpec，得到自身不同MeasureSpec下的宽高
        val resultWidth = resolveSize(width, widthMeasureSpec)
        val resultHeight = resolveSize(startY + lineHeight + paddingBottom, heightMeasureSpec)
        //5.调用setMeasuredDimension保存自己的宽高
        setMeasuredDimension(resultWidth, resultHeight)
    }

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

}