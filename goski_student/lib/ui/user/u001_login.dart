import 'package:easy_localization/easy_localization.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'package:goski_student/const/color.dart';
import 'package:goski_student/const/font_size.dart';
import 'package:goski_student/const/util/screen_size_controller.dart';
import 'package:goski_student/ui/user/u002_signup.dart';
import 'package:logger/logger.dart';

Logger logger = Logger();
final screenSizeController = Get.find<ScreenSizeController>();

class LoginScreen extends StatelessWidget {
  const LoginScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return const BackgroundContainer();
  }
}

class BackgroundContainer extends StatelessWidget {
  const BackgroundContainer({super.key});

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: const BoxDecoration(
        image: DecorationImage(
          image: AssetImage("assets/images/splash.jpg"),
          fit: BoxFit.cover,
        ),
      ),
      child: const LoginContent(),
    );
  }
}

class LoginContent extends StatelessWidget {
  const LoginContent({super.key});

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: <Widget>[
          Container(
            height: screenSizeController.getHeightByRatio(0.33),
          ),
          const LogoImage(),
          const Text(
            'forInstructor',
            style: TextStyle(
              fontFamily: 'RubikGlitch',
              fontSize: goskiFontMedium,
              color: goskiBlack,
              fontWeight: FontWeight.w100,
            ),
          ).tr(),
          Expanded(child: Container()), // 로고와 버튼 사이에 공간
          const KakaoLoginButton(),
          Expanded(child: Container()), // 버튼과 텍스트 사이에 공간
          const BottomText(),
          Container(
            height: screenSizeController.getHeightByRatio(0.03),
          ),
        ],
      ),
    );
  }
}

class LogoImage extends StatelessWidget {
  const LogoImage({super.key});

  @override
  Widget build(BuildContext context) {
    return Image.asset(
      "assets/images/logo.png",
      width: 220, // 로고의 너비
      height: 120, // 로고의 높이
    );
  }
}

class KakaoLoginButton extends StatelessWidget {
  const KakaoLoginButton({super.key});

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: () {
        // 로그인 로직 구현
        // Get.find<LoginController>().login();
        Get.to(() => const SignUpScreen());
      },
      child: Image.asset(
        "assets/images/kakao_login.png",
        width: 300,
        height: 60,
      ),
    );
  }
}

class BottomText extends StatelessWidget {
  const BottomText({super.key});

  @override
  Widget build(BuildContext context) {
    return const Text(
      'loginBottomText',
      style: TextStyle(
        fontFamily: 'RubikGlitch',
        fontSize: goskiFontLarge,
        color: goskiBlack,
      ),
    ).tr();
  }
}
