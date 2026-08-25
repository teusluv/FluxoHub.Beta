import React from 'react';
import { NavigationContainer } from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { useAuth } from '../context/AuthContext';
import { ActivityIndicator, View } from 'react-native';
import { COLORS } from '../constants/theme';

import LoginScreen from '../screens/LoginScreen';
import HomeScreen from '../screens/HomeScreen';
import DetalheScreen from '../screens/DetalheScreen';
import CanhotoScreen from '../screens/CanhotoScreen';
import VendedoresScreen from '../screens/VendedoresScreen';

const Stack = createNativeStackNavigator();
const Tab = createBottomTabNavigator();

function TabNavigator() {
  return (
    <Tab.Navigator
      screenOptions={{
        headerShown: false,
        tabBarStyle: {
          backgroundColor: COLORS.surface,
          borderTopWidth: 1,
          borderTopColor: COLORS.border,
          paddingBottom: 5,
          paddingTop: 5,
          height: 60,
        },
        tabBarActiveTintColor: COLORS.primary,
        tabBarInactiveTintColor: COLORS.textSecondary,
        tabBarLabelStyle: { fontSize: 12, fontWeight: 'bold' },
      }}
    >
      <Tab.Screen 
        name="Motorista" 
        component={HomeScreen} 
        options={{ tabBarLabel: 'Minhas Rotas', tabBarIcon: () => <View style={{width:24,height:24,backgroundColor:COLORS.primary,borderRadius:12,alignItems:'center',justifyContent:'center'}}><View style={{width:10,height:10,backgroundColor:'#FFF',borderRadius:5}}/></View> }}
      />
      <Tab.Screen 
        name="Vendedor" 
        component={VendedoresScreen} 
        options={{ tabBarLabel: 'Vendas (Tracking)', tabBarIcon: () => <View style={{width:24,height:24,backgroundColor:COLORS.warning,borderRadius:12,alignItems:'center',justifyContent:'center'}}><View style={{width:10,height:10,backgroundColor:'#FFF',borderRadius:5}}/></View> }}
      />
    </Tab.Navigator>
  );
}

export default function AppNavigator() {
  const { auth, loading } = useAuth();

  if (loading) {
    return (
      <View style={{ flex: 1, alignItems: 'center', justifyContent: 'center', backgroundColor: COLORS.background }}>
        <ActivityIndicator size="large" color={COLORS.primary} />
      </View>
    );
  }

  return (
    <NavigationContainer>
      <Stack.Navigator screenOptions={{ headerShown: false }}>
        {auth ? (
          <>
            <Stack.Screen name="MainTabs" component={TabNavigator} />
            <Stack.Screen name="Detalhe" component={DetalheScreen} />
            <Stack.Screen name="Canhoto" component={CanhotoScreen} />
          </>
        ) : (
          <Stack.Screen name="Login" component={LoginScreen} />
        )}
      </Stack.Navigator>
    </NavigationContainer>
  );
}
