package snunit.unsafe

import scala.scalanative.meta.LinktimeInfo

private[unsafe] final val StringCharArrayOffset = if (LinktimeInfo.isMultithreadingEnabled) 16 else 8
private[unsafe] final val StringOffsetOffset = if (LinktimeInfo.isMultithreadingEnabled) 24 else 16
private[unsafe] final val StringCountOffset = if (LinktimeInfo.isMultithreadingEnabled) 28 else 20
