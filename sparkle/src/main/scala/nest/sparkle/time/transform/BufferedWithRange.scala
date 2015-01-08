package nest.sparkle.time.transform


///** A DataStream that buffers all initially requested data into a single array.
//  * The DataStream also bundles the range interval request that produced this data for further downstream
//  * processing.
//  */
//case class BufferedWithRange[K:TypeTag, V:TypeTag] // format: OFF
//    (initial: Future[ArrayPair[K,V]], 
//     ongoing: Observable[ArrayPair[K,V]],
//     requestRange: Option[RangeInterval[K]]) 
//    extends DataStream[K,V,BufferedWithRequestRange] with RequestRange[K]
//    { // format: ON
//
//  override def mapData[B: TypeTag] // format: OFF
//      (fn: ArrayPair[K,V] => ArrayPair[K,B])
//      (implicit execution:ExecutionContext)
//      : BufferedWithRequestRange[K,B] = { // format: ON
//    val newInitial = initial.map(fn)
//    val newOngoing = ongoing.map(fn)
//    BufferedWithRequestRange(newInitial, newOngoing, requestRange)
//  }
//  
//  override def keyType = typeTag[K]
//  def valueType = typeTag[V]
//  override def runInitial():Unit = {}
//
//}
 
