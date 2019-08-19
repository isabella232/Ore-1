package ore.external

sealed trait AvailabilityState extends Product with Serializable
object AvailabilityState {
  case object Available      extends AvailabilityState
  case object MaybeAvailable extends AvailabilityState
  case object TimedOut       extends AvailabilityState
  case object Unavailable    extends AvailabilityState
}
