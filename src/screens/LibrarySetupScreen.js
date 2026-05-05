
import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import Svg, { Path } from 'react-native-svg';

function IronLogLogo({ size = 80 }) {
  return (
    <Svg width={size} height={size} viewBox="0 0 1254 1254">
      <Path
        fillRule="evenodd"
        fill="#FFFFFF"
        d="M568.5 206L581 205.5L593 210.5L598.5 216L603.5 226L603.5 299L595.5 316L576 330.5L388 450.5L381.5 457L376.5 469L376.5 776L379.5 784L387 792.5L440 825.5L447.5 835L451.5 847L450.5 926L442 937.5L435 940.5L427 940.5L286 853.5L274.5 842L268.5 828L267.5 418L274.5 398L284 387.5L388 321.5L560 208.5L568.5 206ZM675.5 206L687 205.5L701 210.5L970 385.5L978.5 394L984.5 405L986.5 413L986.5 824L981 829.5L974 830.5L959 822.5L886 777.5L879.5 768L879.5 469L876.5 460L870 451.5L678 328.5L658.5 313L653.5 304L651.5 295L651.5 232L656.5 218L664 210.5L675.5 206ZM575.5 387L585 386.5L592 389.5L599.5 397L602.5 404L602.5 1012L598.5 1021L591 1028.5L584 1031.5L572 1031.5L567 1029.5L501 985.5L492.5 976L487.5 964L487.5 462L494.5 445L502 436.5L568 389.5L575.5 387ZM670.5 387L680 386.5L687 389.5L753 435.5L762.5 447L766.5 458L766.5 835L768.5 840L776 845.5L784 844.5L855 798.5L861 795.5L870 795.5L953 844.5L957.5 850L957.5 858L953 863.5L697 1028.5L685 1032.5L674 1032.5L663 1027.5L655.5 1019L651.5 1007L651.5 409L656.5 396L663 389.5L670.5 387Z"
      />
    </Svg>
  );
}

export default function LibrarySetupScreen({ status }) {
  const msg = status === 'offline'
    ? 'Offline — using bundled exercises.\nFull library will download on next launch.'
    : 'Setting up exercise library...';
  return (
    <View style={s.container}>
      <IronLogLogo size={88} />
      <Text style={s.title}>Ironlog</Text>
      <Text style={s.msg}>{msg}</Text>
    </View>
  );
}

const s = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#080808', alignItems: 'center', justifyContent: 'center', padding: 40, gap: 16 },
  title: { fontSize: 32, fontWeight: '900', color: '#f0f0f0', letterSpacing: -1 },
  msg: { fontSize: 13, color: '#666', textAlign: 'center', letterSpacing: 0.5 },
});
