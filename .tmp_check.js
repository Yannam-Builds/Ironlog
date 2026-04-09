'use strict';

Object.defineProperty(exports, '__esModule', {
  value: true
});

var _slicedToArray = (function () { function sliceIterator(arr, i) { var _arr = []; var _n = true; var _d = false; var _e = undefined; try { for (var _i = arr[Symbol.iterator](), _s; !(_n = (_s = _i.next()).done); _n = true) { _arr.push(_s.value); if (i && _arr.length === i) break; } } catch (err) { _d = true; _e = err; } finally { try { if (!_n && _i['return']) _i['return'](); } finally { if (_d) throw _e; } } return _arr; } return function (arr, i) { if (Array.isArray(arr)) { return arr; } else if (Symbol.iterator in Object(arr)) { return sliceIterator(arr, i); } else { throw new TypeError('Invalid attempt to destructure non-iterable instance'); } }; })();

exports['default'] = FloatingWorkoutWidget;

function _interopRequireWildcard(obj) { if (obj && obj.__esModule) { return obj; } else { var newObj = {}; if (obj != null) { for (var key in obj) { if (Object.prototype.hasOwnProperty.call(obj, key)) newObj[key] = obj[key]; } } newObj['default'] = obj; return newObj; } }

function _interopRequireDefault(obj) { return obj && obj.__esModule ? obj : { 'default': obj }; }

var _react = require('react');

var _react2 = _interopRequireDefault(_react);

var _reactNative = require('react-native');

var _expoVectorIcons = require('@expo/vector-icons');

var _expoHaptics = require('expo-haptics');

var Haptics = _interopRequireWildcard(_expoHaptics);

var _contextActiveWorkoutBannerContext = require('../context/ActiveWorkoutBannerContext');

var _contextThemeContext = require('../context/ThemeContext');

function formatElapsed(ms) {
  var s = Math.floor(ms / 1000);
  var m = Math.floor(s / 60);
  var h = Math.floor(m / 60);
  if (h > 0) return h + ':' + String(m % 60).padStart(2, '0') + ':' + String(s % 60).padStart(2, '0');
  return String(m).padStart(2, '0') + ':' + String(s % 60).padStart(2, '0');
}

function FloatingWorkoutWidget(_ref) {
  var navigation = _ref.navigation;

  var _useActiveBanner = (0, _contextActiveWorkoutBannerContext.useActiveBanner)();

  var banner = _useActiveBanner.banner;

  var colors = (0, _contextThemeContext.useTheme)();

  var _useState = (0, _react.useState)(0);

  var _useState2 = _slicedToArray(_useState, 2);

  var elapsed = _useState2[0];
  var setElapsed = _useState2[1];

  (0, _react.useEffect)(function () {
    if (!banner || !banner.startTime) {
      setElapsed(0);
      return;
    }
    var interval = setInterval(function () {
      setElapsed(Date.now() - banner.startTime);
    }, 1000);
    return function () {
      return clearInterval(interval);
    };
  }, [banner]);

  if (!banner) return null;

  var timerDisplay = banner.startTime ? formatElapsed(elapsed) : '--:--';

  return _react2['default'].createElement(
    _reactNative.TouchableOpacity,
    {
      style: [s.pill, { backgroundColor: colors.accent }],
      onPress: function () {
        Haptics.selectionAsync()['catch'](function () {});
        navigation.navigate('ActiveWorkout', { planIndex: banner.planIndex, dayIndex: banner.dayIndex });
      },
      activeOpacity: 0.85 },
    _react2['default'].createElement(
      _reactNative.View,
      { style: s.left },
      _react2['default'].createElement(_expoVectorIcons.Ionicons, { name: 'barbell-outline', size: 16, color: colors.textOnAccent || '#fff' }),
      _react2['default'].createElement(
        _reactNative.View,
        null,
        _react2['default'].createElement(
          _reactNative.Text,
          { style: [s.name, { color: colors.textOnAccent || '#fff' }], numberOfLines: 1 },
          banner.dayName
        ),
        _react2['default'].createElement(
          _reactNative.Text,
          { style: [s.sub, { color: colors.textOnAccent || 'rgba(255,255,255,0.8)' }] },
          'WORKOUT IN PROGRESS'
        )
      )
    ),
    _react2['default'].createElement(
      _reactNative.View,
      { style: s.right },
      _react2['default'].createElement(
        _reactNative.Text,
        { style: [s.timer, { color: colors.textOnAccent || '#fff' }] },
        timerDisplay
      ),
      _react2['default'].createElement(_expoVectorIcons.Ionicons, { name: 'chevron-forward', size: 16, color: colors.textOnAccent || '#fff', style: { opacity: 0.7 } })
    )
  );
}

var s = _reactNative.StyleSheet.create({
  pill: {
    marginHorizontal: 12,
    marginBottom: 8,
    borderRadius: 14,
    paddingHorizontal: 16,
    paddingVertical: 12,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    elevation: 8,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.3,
    shadowRadius: 8
  },
  left: { flexDirection: 'row', alignItems: 'center', gap: 10, flex: 1 },
  name: { color: '#fff', fontWeight: '800', fontSize: 14, letterSpacing: 0.5 },
  sub: { color: 'rgba(255,255,255,0.75)', fontSize: 9, letterSpacing: 2, marginTop: 1 },
  right: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  timer: { color: '#fff', fontWeight: '900', fontSize: 16, letterSpacing: 1 }
});
module.exports = exports['default'];
